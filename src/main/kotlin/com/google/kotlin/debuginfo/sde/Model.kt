package com.google.kotlin.debuginfo.sde

class SourceDebugExtension(
  val sourceFile: String,
  val defaultStratumName: String,
  val strata: Map<String, Stratum> = mutableMapOf()
) {
  fun remap(outputLine: Int): RemappedLine? =
    strata[defaultStratumName]?.remap(outputLine)

  fun remap(stratumName: String, outputLine: Int): RemappedLine? =
    strata[stratumName]?.remap(outputLine)
}

data class FileInfo(
  val name: String,
  val absoluteName: String? = null
)

class RemappedLine(
  val fileInfo: FileInfo,
  val line: Int
)

class Stratum(
  val name: String,
  val files: Map<Int, FileInfo> = mutableMapOf(),
  val lines: List<LineMapping> = mutableListOf()
) {
  fun remap(outputLine: Int): RemappedLine? {
    for (line in lines) {
      if (outputLine in line) {
        return RemappedLine(
          files.getValue(line.lineFileId),
          line.remap(outputLine)
        )
      }
    }
    return null
  }
}

class LineMapping(
  val inputStartLine: Int,
  val lineFileId: Int,
  val outputStartLine: Int,
  val repeatCount: Int = 1,
  val outputLineIncrement: Int = 1
) {
  override fun toString(): String {
    return "$inputStartLine#$lineFileId,$repeatCount:$outputStartLine,$outputLineIncrement"
  }

  operator fun contains(outputLine: Int): Boolean =
    outputLine in outputStartLine until outputStartLine + outputLineIncrement * repeatCount

  fun remap(outputLine: Int): Int =
    inputStartLine + (outputLine - outputStartLine) / outputLineIncrement
}
