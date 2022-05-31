package com.google.kotlin.debuginfo.sde

private class Scanner(
    private val input: ByteArray
) {
    private var index: Int = 0

    fun peek(c: Char): Boolean =
        index < input.size && input[index] == c.code.toByte()

    private fun isSpace(b: Byte) =
        b.toInt() == '\n'.code || b.toInt() == '\r'.code || b.toInt() == ' '.code

    fun skipWhitespace(): Boolean {
        val start = index
        while (index < input.size && isSpace(input[index])) { index++ }
        return start != index
    }

    fun accept(s: String): Boolean {
        val pattern = s.encodeToByteArray()
        if (index + pattern.size >= input.size) {
            return false
        }
        for ((i, b) in pattern.withIndex()) {
            if (input[index + i] != b) {
                return false
            }
        }
        index += pattern.size
        skipWhitespace()
        return true
    }

    fun token(): String {
        val start = index
        while (index < input.size && !isSpace(input[index])) { index++ }
        return String(input.sliceArray(start until index)).also { skipWhitespace() }
    }

    fun number(): Int {
        val firstIndex = index
        var result = 0
        while (index < input.size && input[index].toInt() in '0'.code .. '9'.code) {
            result = 10 * result + (input[index].toInt() - '0'.code)
            index++
        }
        return if (index != firstIndex) result.also { skipWhitespace() } else -1
    }
}

private fun Scanner.fileSection(files: MutableMap<Int, FileInfo>) {
    while (!peek('*')) {
        val hasAbsoluteFileName = accept("+")
        val fileId = number()
        require(fileId >= 0) {
            "Malformed file section: file id $fileId, name: ${token()}"
        }
        files[fileId] = FileInfo(token(), if (hasAbsoluteFileName) token() else null)
    }
}

private fun Scanner.lineSection(lines: MutableList<LineMapping>) {
    var previousFileId = 0
    while (!peek('*')) {
        val inputStartLine = number()
        val lineFileId = if (accept("#")) number() else previousFileId
        previousFileId = lineFileId
        val repeatCount = if (accept(",")) number() else 1
        require(accept(":"))
        val outputStartLine = number()
        val outputLineIncrement = if (accept(",")) number() else 1
        lines += LineMapping(
            inputStartLine,
            lineFileId,
            outputStartLine,
            repeatCount,
            outputLineIncrement
        )
    }
}

private fun Scanner.stratum(): Stratum? {
    if (!accept("*S")) return null

    val name = token()
    val files = mutableMapOf<Int, FileInfo>()
    val lines = mutableListOf<LineMapping>()

    while (true) {
        when {
            accept("*F") -> {
                fileSection(files)
            }
            accept("*L") -> {
                lineSection(lines)
            }
            accept("*V") -> {
                // ...we don't produce vendor sections, so this shouldn't happen
                error("Unexpected vendor section in SourceDebugExtension ${token()}")
            }
            else -> break
        }
    }

    return Stratum(name, files, lines)
}

fun parseSourceDebugExtension(source: ByteArray): SourceDebugExtension {
    val scanner = Scanner(source)

    // Parse the header
    require(scanner.accept("SMAP")) {
        "Incorrect SourceDebugExtension header: ${scanner.token()}"
    }
    val sourceFile = scanner.token()
    val defaultStratum = scanner.token()
    val strata = mutableMapOf<String, Stratum>()

    while (true) {
        val stratum = scanner.stratum() ?: break
        require(stratum.name !in strata.keys) {
            "Duplicate stratum in SourceDebugExtension ${stratum.name}"
        }
        strata[stratum.name] = stratum
    }

    require(scanner.accept("*E"))
    return SourceDebugExtension(sourceFile, defaultStratum, strata)
}
