package com.metrolist.innertubex.cipher

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerScriptParserTest {
    @Test
    fun extractsFunctionsAcrossLiteralsCommentsRegexAndTemplateExpressions() {
        val code =
            """
            var n = function(a){a=a.split(""); /* } */ var x = /[{}]/; a.reverse(); return a.join("")}
            var sig = function(a){ // }
              a=a.split("");
              const t = `literal } ${'$'}{{value: "}"}}`;
              const q = 'escaped \\\\';
              a.reverse();
              return a.join("")
            }
            """.trimIndent()

        val result = PlayerScriptParser.parse(code)

        assertEquals("n", result.nInvoker)
        assertEquals("sig", result.sigInvoker)
        assertContains(result.nFunctionCode!!, "return a.join")
        assertContains(result.sigFunctionCode!!, "const t")
    }

    @Test
    fun escapedQuoteParityDoesNotEndFunctionEarly() {
        val code = "var f = function(a){a=a.split(\"\"); var s = 'a\\\\'; var o = {x: 1}; return a.join(\"\")}"
        val result = PlayerScriptParser.extractNFunction(code)
        assertContains(result.first!!, "var o = {x: 1}")
    }

    @Test
    fun regexAfterKeywordDoesNotEndFunctionAndMalformedFunctionIsRejected() {
        val code =
            "var f=function(a){a=a.split(\"\");var r=function(){return /}/.test(\"}\")};" +
                "if(a.length) /* } */ /}/.test(\"}\");return a.join(\"\")}"

        assertContains(PlayerScriptParser.extractNFunction(code).first!!, "return /}/")
        assertContains(PlayerScriptParser.extractNFunction(code).first!!, "if(a.length) /* } */ /}/")
        assertNull(PlayerScriptParser.extractNFunction("var f=function(a){a=a.split(\"\");return a.join(\"\")").first)
    }
}
