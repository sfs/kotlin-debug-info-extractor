package com.google.kotlin.debuginfo.model

import com.google.kotlin.debuginfo.sde.FileInfo

class MockMethodInfo(
  val name: String,
  // String pool with source names
  val sourceNames: Array<String>,
  // Source paths corresponding to the sourceNames
  val sourcePaths: Array<String?>,
  // String pool with variable names and descriptors
  val variableNames: Array<String>,
  // Each line is encoded with 4 ints: codeIndex, outputLineNumber, lineNumber, sourceNameIndex
  val allLineLocations: IntArray,
  // Each local variable is encoded with 4 ints: startPc, length, slot, variableNameIndex
  val localVariableTable: IntArray,
  // KotlinDebug segment encoded with 3 ints per line:
  // - index into allLineLocations for the line with the additional segment
  // - line number
  // - source name index
  val kotlinDebugSegment: IntArray,
) {
  companion object {
    fun fromMethodInfo(method: MethodInfo): MockMethodInfo {
      val sde = method.classInfo.sourceDebugExtension
      val sourceFile = method.classInfo.sourceFile ?: "<missing source>"

      // Build the sourceNames array
      val sourceNames = mutableListOf<String>()
      val sourcePaths = mutableListOf<String?>()
      val sourceToIndex = mutableMapOf<FileInfo, Int>()
      fun sourceToIndex(source: FileInfo): Int {
        sourceToIndex[source]?.let { return it }
        return sourceNames.size.also {
          sourceToIndex[source] = it
          sourceNames += source.name
          sourcePaths += source.absoluteName
        }
      }

      // Build the variableNames array
      val variableNames = mutableListOf<String>()
      val variableNameToIndex = mutableMapOf<String, Int>()
      fun variableNameToIndex(name: String, descriptor: String): Int {
        val n = "$name:$descriptor"
        variableNameToIndex[n]?.let { return it }
        return variableNames.size.also {
          variableNameToIndex[n] = it
          variableNames += n
        }
      }

      // Build the allLineLocations array
      val allLineLocations = mutableListOf<Int>()
      for (location in method.lineNumberTable) {
        allLineLocations += location.startPc
        allLineLocations += location.lineNumber
        val remapped = sde?.remap(location.lineNumber)
        if (remapped != null) {
          allLineLocations += remapped.line
          allLineLocations += sourceToIndex(remapped.fileInfo)
        } else {
          allLineLocations += location.lineNumber
          // So this is not really correct. In reality, JDI comes up with a fake path based on the package
          // name of the surrounding class, but this is just plain wrong for Kotlin and we *cannot* rely
          // on it. It's better to throw an exception if any code tries to access the path of a method without SDE.
          allLineLocations += sourceToIndex(FileInfo(sourceFile, null))
        }
      }

      // Build the localVariableTable array
      val localVariableTable = mutableListOf<Int>()
      for (local in method.localVariableTable) {
        localVariableTable += local.startPc
        localVariableTable += local.length
        localVariableTable += local.slot
        localVariableTable += variableNameToIndex(local.name, local.descriptor)
      }

      val kotlinDebugSegment = mutableListOf<Int>()
      for ((index, location) in method.lineNumberTable.withIndex()) {
        val remapped = sde?.remap("KotlinDebug", location.lineNumber)
        if (remapped != null) {
          kotlinDebugSegment += index
          kotlinDebugSegment += remapped.line
          kotlinDebugSegment += sourceToIndex(remapped.fileInfo)
        }
      }

      return MockMethodInfo(
        method.name,
        sourceNames.toTypedArray(),
        sourcePaths.toTypedArray(),
        variableNames.toTypedArray(),
        allLineLocations.toIntArray(),
        localVariableTable.toIntArray(),
        kotlinDebugSegment.toIntArray()
      )
    }
  }
}
