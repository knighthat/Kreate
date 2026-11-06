package com.metrolist.innertubex.cipher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class RemotePlayerConfigParserTest {
    @Test
    fun parsesZemerEntryAndAlias() {
        val result =
            assertIs<RemotePlayerConfigParser.ParseResult.Success>(
                RemotePlayerConfigParser.parse(
                    """
                    {
                      "schemaVersion": 1,
                      "players": {
                        "16ee6936": {
                          "sig": "mP(4,155,INPUT)",
                          "nClass": "Yx",
                          "sts": 20613,
                          "aliases": ["ca366632"]
                        }
                      }
                    }
                    """.trimIndent(),
                ),
            )

        val config = result.configs.getValue("16ee6936")
        assertSame(config, result.configs.getValue("ca366632"))
        assertEquals("mP(4,155,INPUT)", config.sigJsExpression)
        assertEquals(RemotePlayerConfigParser.buildNJsExpression("Yx"), config.nJsExpression)
        assertEquals(20613, config.signatureTimestamp)
    }

    @Test
    fun rejectsFutureSchema() {
        assertIs<RemotePlayerConfigParser.ParseResult.Failure>(
            RemotePlayerConfigParser.parse("""{"schemaVersion":2,"players":{}}"""),
        )
    }

    @Test
    fun buildsFaradayCanonicalCipherPlayerUrl() {
        assertEquals(
            "https://www.youtube.com/s/player/66a6ea83/player_ias.vflset/en_GB/base.js",
            faradayCipherPlayerUrl(
                "https://www.youtube.com/s/player/66a6ea83/player_es6.vflset/pl_PL/base.js",
            ),
        )
    }

    @Test
    fun canonicalizesOnlyLocalizedIasPlayerUrls() {
        assertEquals(
            "https://www.youtube.com/s/player/66a6ea83/player_ias.vflset/en_GB/base.js",
            canonicalPlayerUrl("https://www.youtube.com/s/player/66a6ea83/player_ias.vflset/pl_PL/base.js"),
        )
        val tvPlayer = "https://www.youtube.com/s/player/66a6ea83/tv-player-ias.vflset/tv-player-ias.js"
        assertEquals(tvPlayer, canonicalPlayerUrl(tvPlayer))
    }

    @Test
    fun parsesLegacyNestedSignatureTimestamp() {
        assertEquals(
            20613,
            RemotePlayerConfigParser.parseLegacySignatureTimestamp(
                """{"playerHash":"16ee6936","player":{"sts":20613}}""",
                expectedPlayerHash = "16ee6936",
            ),
        )
    }
}
