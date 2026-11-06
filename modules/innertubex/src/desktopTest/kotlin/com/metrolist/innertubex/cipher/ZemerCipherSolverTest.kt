package com.metrolist.innertubex.cipher

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ZemerCipherSolverTest {
    @Test
    fun evaluatesFaradayExpressionsInsidePlayerIife() =
        runBlocking {
            val parsed =
                assertIs<RemotePlayerConfigParser.ParseResult.Success>(
                    RemotePlayerConfigParser.parse(
                        """
                        {
                          "schemaVersion": 1,
                          "players": {
                            "16ee6936": {
                              "sig": "S(1,2,INPUT)",
                              "nClass": "X",
                              "sts": 20613
                            }
                          }
                        }
                        """.trimIndent(),
                    ),
                )
            val playerCode =
                """
                (function(g){
                Intl.NumberFormat.supportedLocalesOf(["en"]);
                new Intl.DateTimeFormat().resolvedOptions().timeZone;
                g.X=class{get(){return "valid_n_value"}};
                function S(a,b,input){return input.split("").reverse().join("")}
                })(_yt_player);
                """.trimIndent()
            val solver = ZemerCipherSolver.create(playerCode, parsed.configs.getValue("16ee6936"))

            try {
                assertEquals("cba", solver.solveSignature("abc"))
                assertEquals("valid_n_value", solver.solveN("old"))
            } finally {
                solver.dispose()
            }
        }
}
