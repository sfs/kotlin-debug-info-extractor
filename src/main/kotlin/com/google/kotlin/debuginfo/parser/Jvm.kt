package com.google.kotlin.debuginfo.parser

import KOTLIN_METADATA_INTERNAL_NAME
import com.google.kotlin.debuginfo.model.ClassInfo
import com.google.kotlin.debuginfo.model.LocalVariable
import com.google.kotlin.debuginfo.model.Location
import com.google.kotlin.debuginfo.model.MethodInfo
import com.google.kotlin.debuginfo.sde.SourceDebugExtension
import com.google.kotlin.debuginfo.sde.parseSourceDebugExtension
import java.io.DataInput
import java.io.File
import org.apache.bcel.Const
import org.apache.bcel.classfile.Attribute
import org.apache.bcel.classfile.ClassParser
import org.apache.bcel.classfile.ConstantPool
import org.apache.bcel.classfile.UnknownAttributeReader
import org.apache.bcel.classfile.Visitor

const val SOURCE_DEBUG_EXTENSION = "SourceDebugExtension"

@Suppress("unused")
val SourceDebugExtensionAttributeReader = object : UnknownAttributeReader {
  init {
    Attribute.addAttributeReader(SOURCE_DEBUG_EXTENSION, this)
  }

  override fun createAttribute(
    nameIndex: Int,
    length: Int,
    file: DataInput,
    constantPool: ConstantPool,
  ) = SourceDebugExtensionAttribute(
    nameIndex,
    length,
    parseSourceDebugExtension(ByteArray(length).also { file.readFully(it) }),
    constantPool
  )
}

class SourceDebugExtensionAttribute(
  nameIndex: Int,
  length: Int,
  val contents: SourceDebugExtension,
  constantPool: ConstantPool
) : Attribute(Const.ATTR_UNKNOWN, nameIndex, length, constantPool) {
  override fun accept(v: Visitor) {}
  override fun copy(c: ConstantPool): Attribute { TODO("Not yet implemented") }

  override fun toString(): String {
    return "$SOURCE_DEBUG_EXTENSION:\n$contents"
  }
}

fun extractMethodInfoJvm(file: File): List<MethodInfo> {
  val classFile = ClassParser(file.path).parse()

  val isKotlin = classFile.annotationEntries.any { annotation ->
    annotation.annotationType == KOTLIN_METADATA_INTERNAL_NAME
  }
  if (!isKotlin) {
    return emptyList()
  }

  val classInfo = ClassInfo(
    thisClass = classFile.className,
    sourceFile = classFile.sourceFileName,
    sourceDebugExtension = classFile.attributes
      .filterIsInstance<SourceDebugExtensionAttribute>()
      .firstOrNull()
      ?.contents
  )

  return classFile.methods.map { method ->
    MethodInfo(
      name = method.name,
      descriptor = method.signature,
      flags = method.accessFlags,
      lineNumberTable = method.lineNumberTable?.lineNumberTable?.map {
        Location(startPc = it.startPC, lineNumber = it.lineNumber)
      }?.toTypedArray() ?: emptyArray(),
      localVariableTable = method.localVariableTable?.localVariableTable?.map {
        LocalVariable(
          startPc = it.startPC,
          length = it.length,
          name = it.name,
          descriptor = it.signature,
          slot = it.index,
          // signature = null
        )
      }?.toTypedArray() ?: emptyArray(),
      classInfo = classInfo
    )
  }
}
