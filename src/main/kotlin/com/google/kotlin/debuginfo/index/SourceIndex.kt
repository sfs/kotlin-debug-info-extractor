package com.google.kotlin.debuginfo.index

import com.google.kotlin.debuginfo.model.ClassInfo
import java.io.File
import java.nio.charset.Charset

// Heuristic mapping from ClassInfo to source file.
class SourceIndex(private val root: File) {
  private val fileNameToPaths =
    root.walk().filter { it.extension == "kt" }.groupBy { it.name }

  val commitHash = Runtime.getRuntime().exec("git rev-parse HEAD", null, root)
    .inputStream.readAllBytes().toString(Charset.defaultCharset()).trimEnd()

  operator fun get(classInfo: ClassInfo): String? {
    val source = classInfo.sourceFile ?: return null
    val candidates = fileNameToPaths[source] ?: return null
    val result = if (candidates.size > 1) {
      val packagePath = classInfo.thisClass
        .substringBeforeLast('.')
        .replace('.', File.separatorChar)
      candidates.singleOrNull { it.parent.endsWith(packagePath) }
    } else {
      candidates.single()
    }
    return result?.toRelativeString(root)
  }
}
