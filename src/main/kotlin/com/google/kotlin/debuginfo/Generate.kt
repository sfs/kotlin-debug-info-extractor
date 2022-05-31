package com.google.kotlin.debuginfo

import com.google.kotlin.debuginfo.index.SourceIndex
import com.google.kotlin.debuginfo.model.MethodInfoPair
import com.google.kotlin.debuginfo.model.MockMethodInfo
import java.io.File
import java.util.*

private inline fun generateTests(
  methods: List<MethodInfoPair>,
  testName: String,
  index: SourceIndex?,
  body: StringBuilder.(MethodInfoPair) -> Unit
) {
  val output = StringBuilder()
  output.append("""
    // Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
    package org.jetbrains.kotlin.idea.debugger.test
    
    import org.jetbrains.kotlin.idea.debugger.test.mock.MockMethodInfo
    
    @Suppress("SpellCheckingInspection")
    class $testName : AbstractLocalVariableTableTest() {
    
  """.trimIndent())

  for ((i,method) in methods.withIndex()) {
    val jvm = method.jvm!!
    val classShortName =
      jvm.classInfo.thisClass.substringAfterLast('.').substringBefore('$')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val methodName = when (val name = jvm.name) {
      "<init>" -> "Init"
      "<clinit>" -> "Static"
      else -> name.substringBefore('$')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    output.indented(1, "// ${jvm.classInfo.thisClass.substringAfterLast(".")}.${jvm.name} in ${jvm.classInfo.sourceFile}\n")
    if (index != null) {
      val file = index[jvm.classInfo]
      if (file != null) {
        output.indented(1, "// https://github.com/JetBrains/kotlin/blob/${index.commitHash}/")
        output.append(file)
        output.appendLine("#L${jvm.lineNumberTable.first().lineNumber}")
      }
    }

    output.indented(1, "fun test$classShortName$methodName() {\n")
    output.body(method)
    output.indented(1, "}\n")
    if (i != methods.size - 1) {
      output.appendLine()
    }
  }
  output.appendLine("}")

  File("generated/${testName}.kt").writeText(output.toString())
}

fun generateManualTests(
  methods: List<MethodInfoPair>,
  testName: String,
  index: SourceIndex?,
) {
  generateTests(methods, testName, index) { method ->
    indented(2, "doKotlinInlineStackTest(\n")
    indented(3, "codeIndex = 0,\n")
    indented(3, "methodInfo = ")
    appendMockMethodInfo(MockMethodInfo.fromMethodInfo(method.jvm!!))
    appendLine(",")
    indented(3, "expectation = \"\"\"\n")
    indented(4, "\n")
    indented(3, "\"\"\".trimIndent()\n")
    indented(2, ")\n")
  }
}

fun generateComparisonTests(
  methods: List<MethodInfoPair>,
  testName: String,
  testMethod: String,
  index: SourceIndex?,
) {
  generateTests(methods, testName, index) { method ->
    indented(2, "$testMethod(\n")
    indented(3, "jvm = ")
    appendMockMethodInfo(MockMethodInfo.fromMethodInfo(method.jvm!!))
    appendLine(",")
    indented(3, "dex = ")
    appendMockMethodInfo(MockMethodInfo.fromMethodInfo(method.jvm!!))
    appendLine()
    indented(2, ")\n")
  }
}

fun StringBuilder.appendMockMethodInfo(info: MockMethodInfo) {
  appendLine("MockMethodInfo(")
  indented(4, "name = \"${info.name.replace("$", "\\$")}\",\n")
  indented(4, "sourceNames = ")
  appendStringArray(info.sourceNames)
  appendLine(",")
  indented(4, "sourcePaths = ")
  appendStringArray(info.sourcePaths)
  appendLine(",")
  indented(4, "variableNames = ")
  appendStringArray(info.variableNames)
  appendLine(",")
  indented(4, "allLineLocations = ")
  appendIntArray(info.allLineLocations, 4)
  appendLine(",")
  indented(4, "localVariableTable = ")
  appendIntArray(info.localVariableTable, 4)
  appendLine(",")
  indented(4, "kotlinDebugSegment = ")
  appendIntArray(info.kotlinDebugSegment, 3)
  appendLine()
  indented(3, ")")
}

fun <T : String?> StringBuilder.appendStringArray(array: Array<T>) {
  if (array.isEmpty()) {
    append("arrayOf()")
    return
  }

  appendLine("arrayOf(")
  for ((i, element) in array.withIndex()) {
    if (element != null) {
      indented(5, "\"${element.replace("$", "\\$")}\"")
    } else {
      indented(5, "null")
    }
    if (i != array.size - 1) {
      appendLine(",")
    } else {
      appendLine()
    }
  }
  indented(4, ")")
}

private fun StringBuilder.appendIntArray(array: IntArray, k: Int) {
  if (array.isEmpty()) {
    append("intArrayOf()")
    return
  }

  appendLine("intArrayOf(")
  indent(5)
  val pad = 2 + array.maxOf { it.toString().length }
  for ((i, v) in array.withIndex()) {
    if ((i + 1) % k == 0) {
      appendLine("$v,")
      if (i != array.size - 1) {
        indent(5)
      }
    } else {
      append("$v,".padEnd(pad))
    }
  }
  indented(4, ")")
}

private const val TAB = "    "

private fun StringBuilder.indent(amount: Int) {
  repeat(amount) { append(TAB) }
}

private fun StringBuilder.indented(indent: Int, out: String) {
  indent(indent)
  append(out)
}
