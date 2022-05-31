// Translate dex debug info to jvm debug info.
// See https://android.googlesource.com/platform/art/+/refs/heads/master/libdexfile/dex/dex_file-inl.h
package com.google.kotlin.debuginfo.parser

import KOTLIN_METADATA_INTERNAL_NAME
import com.google.kotlin.debuginfo.model.ClassInfo
import com.google.kotlin.debuginfo.model.LocalVariable
import com.google.kotlin.debuginfo.model.Location
import com.google.kotlin.debuginfo.model.MethodInfo
import com.google.kotlin.debuginfo.sde.parseSourceDebugExtension
import java.io.File
import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.debug.DebugItem
import org.jf.dexlib2.iface.debug.EndLocal
import org.jf.dexlib2.iface.debug.EpilogueBegin
import org.jf.dexlib2.iface.debug.LineNumber
import org.jf.dexlib2.iface.debug.LocalInfo
import org.jf.dexlib2.iface.debug.PrologueEnd
import org.jf.dexlib2.iface.debug.RestartLocal
import org.jf.dexlib2.iface.debug.StartLocal
import org.jf.dexlib2.iface.value.StringEncodedValue

const val SOURCE_DEBUG_EXTENSION_TYPE = "Ldalvik/annotation/SourceDebugExtension;"

fun extractMethodInfoDex(file: File): List<MethodInfo> =
  DexFileFactory.loadDexFile(file, Opcodes.getDefault()).classes.filter {
    it.annotations.any { annotation ->
      annotation.type == KOTLIN_METADATA_INTERNAL_NAME
    }
  }.flatMap { classDef ->
    // As far as I know the SDE is just copied from the original class into an annotation
    // with no further processing. OTOH there's no harm in deserializing it in both dex and
    // classfiles.
    val sourceDebugExtension = (classDef.annotations.firstOrNull {
      it.type == SOURCE_DEBUG_EXTENSION_TYPE
    }?.elements?.singleOrNull()?.value as? StringEncodedValue)?.value

    val classInfo = ClassInfo(
      thisClass = classDef.type.run { substring(1, length - 1).replace('/', '.') },
      sourceFile = classDef.sourceFile,
      sourceDebugExtension = sourceDebugExtension?.let {
        parseSourceDebugExtension(it.encodeToByteArray())
      }
    )

    classDef.methods.map { method ->
      method.extractMethodInfo(classInfo)
    }
  }

private fun Method.extractMethodInfo(classInfo: ClassInfo) = MethodInfo(
  name = name,
  descriptor = descriptor,
  flags = accessFlags,
  lineNumberTable = extractLineNumberTable(),
  localVariableTable = extractLocalVariableTable(),
  classInfo = classInfo
)

private val Method.descriptor: String
  get() = "(${parameterTypes.joinToString("")})$returnType"

private fun Method.extractLineNumberTable(): Array<Location> {
  val implementation = implementation ?: return arrayOf()
  return implementation.debugItems.filterIsInstance<LineNumber>().map {
    Location(
      startPc = it.codeAddress,
      lineNumber = it.lineNumber
    )
  }.toTypedArray()
}

private val Method.isStatic: Boolean
  get() = AccessFlags.STATIC.isSet(accessFlags)

private fun typeSize(type: String): Int =
  if (type == "J" || type == "D") 2 else 1

// This is stored in a field in the code item, but it should just be computable from the parameters.
private val Method.insSize: Int
  get() = parameters.sumOf { typeSize(it.type) } + if (isStatic) 0 else 1

private data class Register(
  val startPc: Int,
  val name: String,
  val descriptor: String,
  val signature: String? = null,
)

private fun Method.extractLocalVariableTable(): Array<LocalVariable> {
  val implementation = implementation ?: return arrayOf()
  val registers = arrayOfNulls<Register?>(implementation.registerCount)
  val results = mutableListOf<LocalVariable>()

  // ART does not produce any debug information for the parameters if there are missing names.
  // We ignore these methods.
  if (parameters.any { it.name == null }) {
    return arrayOf()
  }

  // Add the named parameters. It looks like the code in openjvmti would fail for
  // methods where some parameters are unnamed, so that probably never happens in
  // practice.
  var currentRegister = implementation.registerCount - insSize

  if (!isStatic) {
    registers[currentRegister++] = Register(
      startPc = 0,
      name = "this",
      descriptor = definingClass
    )
  }

  for (parameter in parameters) {
    registers[currentRegister] = Register(
      startPc = 0,
      name = parameter.name ?: continue,
      descriptor = parameter.type,
      signature = parameter.signature
    )
    currentRegister += typeSize(parameter.type)
  }

  // Add the register at `index` to the results as a local variable ending at `endPc`.
  fun emit(index: Int, endPc: Int) {
    val register = registers[index]
    // Really, ART just silently drops this error, but if we did get here then there's probably
    // a bug in D8 which is good to know...
    require(register != null) {
      "Register $index is undefined at $endPc"
    }
    results += LocalVariable(
      startPc = register.startPc,
      length = endPc - register.startPc,
      name = register.name,
      descriptor = register.descriptor,
      slot = index,
      // signature = register.signature,
    )
    registers[index] = null
  }

  // Start a new local variable for the given `debugItem` at `index`, implicitly
  // ending any existing local at `index`.
  fun <T> startLocal(debugItem: T, index: Int) where T : DebugItem, T : LocalInfo {
    if (registers[index] != null) {
      emit(index, debugItem.codeAddress)
    }
    registers[index] = Register(
      startPc = debugItem.codeAddress,
      name = debugItem.name!!,
      descriptor = debugItem.type!!,
      signature = debugItem.signature,
    )
  }

  for (debugItem in implementation.debugItems) {
    when (debugItem) {
      is StartLocal -> {
        // If the register is live it ends here.
        if (registers[debugItem.register] != null) {
          emit(debugItem.register, debugItem.codeAddress)
        }
        startLocal(debugItem, debugItem.register)
      }
      is RestartLocal -> {
        // ART doesn't restart locals which are already live.
        if (registers[debugItem.register] == null) {
          startLocal(debugItem, debugItem.register)
        }
      }
      is EndLocal -> emit(debugItem.register, debugItem.codeAddress)
      // Nothing actually emits SetSource, so we should never encounter it.
      else -> require(debugItem is LineNumber || debugItem is PrologueEnd || debugItem is EpilogueBegin) {
        "Unknown debug item in dex method $this: $debugItem"
      }
    }
  }

  if (registers.all { it == null }) {
    return results.toTypedArray()
  }

  // It's silly that we have to compute the insns_size field by parsing all the instructions,
  // but that's just how dexlib2 is implemented...
  val insnsSize = implementation.instructions.sumOf { it.codeUnits }
  for (index in registers.indices) {
    if (registers[index] != null) {
      emit(index, insnsSize)
    }
  }

  return results.toTypedArray()
}
