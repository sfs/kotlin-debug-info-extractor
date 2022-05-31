package com.google.kotlin.debuginfo.model

import com.google.kotlin.debuginfo.sde.SourceDebugExtension

/**
 * An entry in the `LineNumberTable` of a JVM method (JVMS 4.7.12).
 */
class Location(
  // Offset into the code array of the method where the code for a
  // new line in the original source file begins.
  val startPc: Int,
  // Corresponding line number in the original source file.
  // Typically, this points past the end of the source file for calls
  // to inline functions, which need to be remapped using the
  // SourceDebugExtensions attribute.
  val lineNumber: Int,
)

/**
 * An entry in the `LocalVariableTable` and `LocalVariableTypeTable` of a JVM method
 * (JVMS 4.7.13 and 4.7.14).
 */
class LocalVariable(
  // Offsets into the code array of the method where the local variable has a value.
  // Concretely a debugger should be able to access the value of this variable in `slot`
  // for any pc in [startPc, startPc + length). For variables which are defined until
  // the end of the method, startPc + length must be the first index past the end of
  // the code array.
  val startPc: Int,
  val length: Int,
  // An unqualified name for the variable, e.g., a non-empty string which
  // doesn't contain the characters . ; [ /
  val name: String,
  // A field descriptor (JVMS 4.3.2) encoding the (erasure of the) type of this
  // local variable in the source program.
  val descriptor: String,
  // The index in the local variable array of the current frame where the value of
  // the variable can be found. For variables of type long or double the value is
  // stored in indices `slot` and `slot + 1`, otherwise it is stored in `slot`.
  val slot: Int,
  // A field signature (JVMS 4.7.9.1) encoding the type of this local variable
  // in the source program. This only exists for variables with a parameterized
  // type.
  //val signature: String? = null,
)

class MethodInfo(
  val name: String,
  val descriptor: String,
  val flags: Int,
  val lineNumberTable: Array<Location>,
  val localVariableTable: Array<LocalVariable>,
  val classInfo: ClassInfo,
)

class ClassInfo(
  val thisClass: String,
  val sourceFile: String?,
  val sourceDebugExtension: SourceDebugExtension? = null,
)

class MethodInfoPair(
  var jvm: MethodInfo? = null,
  var dex: MethodInfo? = null,
)
