package com.metrolist.innertubex.extraction.potoken

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WebPageAttestationChallengeTest {
    @Test
    fun interpreterUrlRequiresTrustedEndpoint() {
        assertEquals(
            "https://www.gstatic.com/botguard.js",
            requireTrustedAttestationInterpreterUrl("//www.gstatic.com/botguard.js"),
        )
        assertEquals(
            "https://www.google.com/js/th/Fg54iyAtrBqvUWW6KE6BOe068ROoHjedFsFuJo_8vFk.js",
            requireTrustedAttestationInterpreterUrl(
                "https://www.google.com/js/th/Fg54iyAtrBqvUWW6KE6BOe068ROoHjedFsFuJo_8vFk.js",
            ),
        )
        listOf(
            "https://example.com/botguard.js",
            "http://www.gstatic.com/botguard.js",
            "https://www.gstatic.com:8443/botguard.js",
            "https://user@www.gstatic.com/botguard.js",
            "https://www.gstatic.com/other.js",
            "https://www.gstatic.com/js/th/Fg54iyAtrBqvUWW6KE6BOe068ROoHjedFsFuJo_8vFk.js",
            "https://www.google.com/js/th/short.js",
            "https://www.google.com/js/th/valid_but_nested/path.js",
        ).forEach { value ->
            assertFailsWith<PoTokenException> { requireTrustedAttestationInterpreterUrl(value) }
        }
    }

    @Test
    fun extractsPairedChallengeAndEventId() {
        val page = "ytcfg.set({\"EVENT_ID\":\"page-event\"}); window.ytAtN({R:'{\"bgChallenge\":{\"program\":\"paired-program\"}}'});"
        val context = requireNotNull(extractWebPageAttestationContext(page))
        assertEquals("page-event", context.eventId)
        assertContains(context.challenge, "paired-program")
    }

    @Test
    fun extractsLegacyAssignmentAndQuotedCall() {
        assertContains(
            requireNotNull(
                extractWebPageAttestationChallenge("window.ytAtR = '{\\\"bgChallenge\\\":{\\\"globalName\\\":\\\"legacy\\\"}}';"),
            ),
            "legacy",
        )
        assertContains(
            requireNotNull(
                extractWebPageAttestationChallenge(
                    "window.ytAtN('bootstrap'); window . ytAtN({'R':'{\\\"bgChallenge\\\":{\\\"program\\\":\\\"test\\\"}}'});",
                ),
            ),
            "test",
        )
    }

    @Test
    fun rejectsUnpairedAndUnterminatedInput() {
        assertNull(extractWebPageAttestationContext("ytcfg.set({\"EVENT_ID\":\"event\"});"))
        assertNull(extractWebPageAttestationChallenge("window.ytAtR = '{\\\"bgChallenge\\\":{}';"))
        assertNull(extractWebPageAttestationChallenge("window.ytAtN({R:'unterminated});"))
    }

    @Test
    fun boundsInputsAndTokenBytes() {
        assertFailsWith<PoTokenException> { extractWebPageAttestationChallenge("x".repeat(4 * 1024 * 1024 + 1)) }
        assertFailsWith<PoTokenException> { stringToU8("x".repeat(1025)) }
        assertFailsWith<PoTokenException> { u8ToBase64("0,256") }
        assertFailsWith<PoTokenException> { u8ToBase64("0,-1") }
        assertFailsWith<PoTokenException> { parseIntegrityTokenData("[]") }
        assertFailsWith<PoTokenException> { parseIntegrityTokenData("[\"AQID\",1,2,3]") }
        assertFailsWith<PoTokenException> { parseIntegrityTokenData("[\"not base64!\",1]") }
    }

    @Test
    fun parsesIntegrityTokenWithStandardAndUrlSafeBase64() {
        assertEquals("new Uint8Array([1,2,3])", parseIntegrityTokenData("[\"AQID\",120]").tokenJavaScript)
        assertEquals("new Uint8Array([1,2,3])", parseIntegrityTokenData("[\"AQID\",120,60]").tokenJavaScript)
        assertEquals("new Uint8Array([251,239])", parseIntegrityTokenData("[\"--8=\",120]").tokenJavaScript)
    }

    @Test
    fun redactsSensitiveToStrings() {
        val context = WebPageAttestationContext("secret-challenge", "secret-event")
        val integrity = IntegrityTokenData("new Uint8Array([1,2])", 42)
        assertEquals("WebPageAttestationContext(challenge=16 chars, eventId=12 chars)", context.toString())
        assertEquals("IntegrityTokenData(tokenJavaScript=21 chars, lifetimeSeconds=42)", integrity.toString())
    }
}
