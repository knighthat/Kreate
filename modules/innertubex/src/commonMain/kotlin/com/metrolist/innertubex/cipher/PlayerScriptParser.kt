package com.metrolist.innertubex.cipher

/**
 * Parser for extracting cipher functions from YouTube player JavaScript.
 *
 * Heuristic extraction similar in spirit to yt-dlp: find split/join transform
 * functions and the object helpers they use. N-transform and signature decipher
 * often share a similar shape; we disambiguate by order (second match) and body checks.
 */
internal object PlayerScriptParser {
    data class ParseResult(
        val nFunctionCode: String?,
        /** JS expression that invokes the n-transform, e.g. variable name `abc`. */
        val nInvoker: String?,
        val sigFunctionCode: String?,
        /** JS expression that invokes the signature decipher, e.g. variable name `xyz`. */
        val sigInvoker: String?,
        val helperFunctions: Map<String, String>,
    )

    private val functionPattern =
        Regex("""(?:var\s+)?([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*function\s*\(\s*a\s*\)\s*\{""")

    /**
     * N-parameter transform: first split/join-style function in the player is often (not always) the n-function.
     */
    fun extractNFunction(playerCode: String): Pair<String?, String?> = extractSplitJoinByIndex(playerCode, index = 0)

    /**
     * Signature decipher: next split/join function after the n-transform that has distinct body.
     */
    fun extractSigFunction(
        playerCode: String,
        nCode: String?,
    ): Pair<String?, String?> {
        val matches = splitJoinFunctions(playerCode)
        for (i in 1 until matches.size) {
            val functionName = matches[i].name
            val func = extractFunctionByName(playerCode, functionName) ?: continue
            if (func != nCode) return func to functionName
        }
        if (matches.isNotEmpty() && nCode == null) {
            extractSplitJoinByIndex(playerCode, index = 1).let { if (it.first != null) return it }
        }

        val altPattern =
            Regex("""(?s)function\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*\(\s*a\s*\)\s*\{[^{}]*a\s*[=.][^{}]*reverse[^{}]*\}""")
        altPattern.findAll(playerCode).forEach { m ->
            val funcName = m.groupValues[1]
            val func = extractFunctionByName(playerCode, funcName)
            if (func != null && func != nCode && (func.contains("reverse") || func.contains("splice") || func.contains("slice"))) {
                return func to funcName
            }
        }
        return null to null
    }

    private fun extractSplitJoinByIndex(
        playerCode: String,
        index: Int,
    ): Pair<String?, String?> {
        val matches = splitJoinFunctions(playerCode)
        if (matches.size <= index) return null to null
        val functionName = matches[index].name
        val func = extractFunctionByName(playerCode, functionName) ?: return null to null
        return func to functionName
    }

    fun extractHelperFunctions(playerCode: String): Map<String, String> {
        val helpers = mutableMapOf<String, String>()
        val helperPatterns =
            listOf(
                Regex("""(?s)var\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*\{\s*[^}]*reverse[^}]*\}"""),
                Regex("""(?s)var\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*\{\s*[^}]*slice[^}]*\}"""),
                Regex("""(?s)var\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*\{\s*[^}]*splice[^}]*\}"""),
            )
        for (pattern in helperPatterns) {
            pattern.findAll(playerCode).forEach { match ->
                val startIdx = match.range.first
                val endIdx = findFunctionEnd(playerCode, startIdx)
                if (endIdx > startIdx) {
                    val fullFunc = playerCode.substring(startIdx, endIdx)
                    val funcName = match.groupValues[1]
                    helpers[funcName] = fullFunc
                }
            }
        }
        return helpers
    }

    fun parse(playerCode: String): ParseResult {
        val (nCode, nInv) = extractNFunction(playerCode)
        val (sigCode, sigInv) = extractSigFunction(playerCode, nCode)
        return ParseResult(
            nFunctionCode = nCode,
            nInvoker = nInv,
            sigFunctionCode = sigCode,
            sigInvoker = sigInv,
            helperFunctions = extractHelperFunctions(playerCode),
        )
    }

    private fun extractFunctionByName(
        playerCode: String,
        functionName: String,
    ): String? {
        val patterns =
            listOf(
                Regex("""(?s)var\s+${Regex.escape(functionName)}\s*=\s*function\s*\([^)]*\)\s*\{"""),
                Regex("""(?s)function\s+${Regex.escape(functionName)}\s*\([^)]*\)\s*\{"""),
                Regex("""(?s)${Regex.escape(functionName)}\s*=\s*function\s*\([^)]*\)\s*\{"""),
            )
        for (pattern in patterns) {
            val match = pattern.find(playerCode)
            if (match != null) {
                val startIdx = match.range.first
                val endIdx = findFunctionEnd(playerCode, startIdx)
                if (endIdx > startIdx) {
                    return playerCode.substring(startIdx, endIdx)
                }
            }
        }
        return null
    }

    private fun findFunctionEnd(
        code: String,
        startIdx: Int,
    ): Int {
        var braceCount = 0
        var state = ScanState.CODE
        var stringChar = '\u0000'
        var escaped = false
        var regexClass = false
        var skipNext = false
        var regexAfterControlParenthesis = false
        val controlParentheses = mutableListOf<Boolean>()
        for (i in startIdx until code.length) {
            if (skipNext) {
                skipNext = false
                continue
            }
            val char = code[i]
            when {
                state == ScanState.LINE_COMMENT -> {
                    if (char == '\n') state = ScanState.CODE
                }

                state == ScanState.BLOCK_COMMENT -> {
                    if (char == '*' && code.getOrNull(i + 1) == '/') {
                        state = ScanState.CODE
                        skipNext = true
                    }
                }

                state == ScanState.STRING || state == ScanState.TEMPLATE -> {
                    if (escaped) {
                        escaped = false
                    } else if (char == '\\') {
                        escaped = true
                    } else if (char == stringChar) {
                        state = ScanState.CODE
                    }
                }

                state == ScanState.REGEX -> {
                    if (escaped) {
                        escaped = false
                    } else if (char == '\\') {
                        escaped = true
                    } else if (char == '[') {
                        regexClass = true
                    } else if (char == ']') {
                        regexClass = false
                    } else if (char == '/' && !regexClass) {
                        state = ScanState.CODE
                    }
                }

                char == '/' && code.getOrNull(i + 1) == '/' -> {
                    state = ScanState.LINE_COMMENT
                }

                char == '/' && code.getOrNull(i + 1) == '*' -> {
                    state = ScanState.BLOCK_COMMENT
                }

                char == '"' || char == '\'' -> {
                    state = ScanState.STRING
                    stringChar = char
                    escaped = false
                }

                char == '`' -> {
                    state = ScanState.TEMPLATE
                    stringChar = '`'
                    escaped = false
                    regexAfterControlParenthesis = false
                }

                char == '/' && (regexAfterControlParenthesis || looksLikeRegex(code, i)) -> {
                    state = ScanState.REGEX
                    escaped = false
                    regexClass = false
                    regexAfterControlParenthesis = false
                }

                char == '(' -> {
                    controlParentheses += previousIdentifier(code, i) in CONTROL_PARENTHESIS_KEYWORDS
                    regexAfterControlParenthesis = false
                }

                char == ')' -> {
                    regexAfterControlParenthesis = controlParentheses.removeLastOrNull() == true
                }

                char == '{' -> {
                    braceCount++
                    regexAfterControlParenthesis = false
                }

                char == '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        return i + 1
                    }
                    regexAfterControlParenthesis = false
                }

                !char.isWhitespace() -> {
                    regexAfterControlParenthesis = false
                }
            }
        }
        return -1
    }

    private enum class ScanState { CODE, STRING, TEMPLATE, LINE_COMMENT, BLOCK_COMMENT, REGEX }

    private fun looksLikeRegex(
        code: String,
        index: Int,
    ): Boolean {
        var i = index - 1
        while (i >= 0 && code[i].isWhitespace()) i--
        if (i < 0 || code[i] in "=([{,:;!&|?") return true
        if (code[i] == '>' && code.getOrNull(i - 1) == '=') return true
        if (!code[i].isLetterOrDigit() && code[i] !in "_$") return false
        val end = i + 1
        while (i >= 0 && (code[i].isLetterOrDigit() || code[i] in "_$")) i--
        return code.substring(i + 1, end) in REGEX_PREFIX_KEYWORDS
    }

    private fun previousIdentifier(
        code: String,
        index: Int,
    ): String {
        var end = index
        while (end > 0 && code[end - 1].isWhitespace()) end--
        var start = end
        while (start > 0 && (code[start - 1].isLetterOrDigit() || code[start - 1] in "_$")) start--
        return code.substring(start, end)
    }

    private fun splitJoinFunctions(code: String): List<FunctionMatch> =
        functionPattern
            .findAll(code)
            .mapNotNull { match ->
                val end = findFunctionEnd(code, match.range.first)
                if (end <= match.range.first) return@mapNotNull null
                val body = code.substring(match.range.first, end)
                if (Regex("""a\s*=\s*a\.split\s*\(\s*""\s*\)""").containsMatchIn(body) &&
                    Regex("""return\s+a\.join\s*\(\s*""\s*\)""").containsMatchIn(body)
                ) {
                    FunctionMatch(match.groupValues[1])
                } else {
                    null
                }
            }.toList()

    private data class FunctionMatch(
        val name: String,
    )

    private val REGEX_PREFIX_KEYWORDS =
        setOf("await", "case", "delete", "do", "else", "in", "instanceof", "new", "of", "return", "throw", "typeof", "void", "yield")

    private val CONTROL_PARENTHESIS_KEYWORDS = setOf("catch", "for", "if", "switch", "while", "with")

    fun generateSolverScript(parseResult: ParseResult): String {
        val sb = StringBuilder()
        parseResult.helperFunctions.forEach { (_, code) ->
            sb.appendLine(code)
        }

        parseResult.nFunctionCode?.let { code ->
            sb.appendLine(code)
            val inv = parseResult.nInvoker
            if (!inv.isNullOrBlank()) {
                sb.appendLine(
                    """
                    function _solveN(input) {
                        try { return $inv(input); } catch(e) { return null; }
                    }
                    """.trimIndent(),
                )
            }
        }

        parseResult.sigFunctionCode?.let { code ->
            sb.appendLine(code)
            val inv = parseResult.sigInvoker
            if (!inv.isNullOrBlank()) {
                sb.appendLine(
                    """
                    function _solveSig(input) {
                        try { return $inv(input); } catch(e) { return null; }
                    }
                    """.trimIndent(),
                )
            }
        }

        return sb.toString()
    }
}
