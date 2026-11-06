package com.metrolist.innertubex.cipher

import kotlinx.coroutines.CancellationException

internal class ZemerCipherSolver private constructor(
    private val engine: QuickJsEngine,
) {
    companion object {
        private const val PLAYER_IIFE_TRAILER = "})(_yt_player);"
        private const val N_PROBE_INPUT = "KdrqFlzJXl9EcCwlmEy"
        private val VALID_N_RESULT = Regex("^[A-Za-z0-9_-]+$")

        suspend fun create(
            playerCode: String,
            config: RemotePlayerConfigParser.HardcodedPlayerConfig,
        ): ZemerCipherSolver {
            val engine = QuickJsEngine()
            try {
                engine.initialize()
                engine.setupYoutubeGlobals()
                engine.execute("globalThis._yt_player = globalThis._yt_player || {};")
                engine.execute(buildModifiedPlayerScript(playerCode, config))

                val probe = engine.callFunction("_nTransformFunc", N_PROBE_INPUT)
                check(
                    probe != null &&
                        probe != N_PROBE_INPUT &&
                        probe.length >= 5 &&
                        VALID_N_RESULT.matches(probe),
                ) { "Faraday n-transform probe failed" }
                return ZemerCipherSolver(engine)
            } catch (e: CancellationException) {
                engine.dispose()
                throw e
            } catch (e: Exception) {
                engine.dispose()
                throw e
            }
        }

        internal fun buildModifiedPlayerScript(
            playerCode: String,
            config: RemotePlayerConfigParser.HardcodedPlayerConfig,
        ): String {
            val sigExpression = requireNotNull(config.sigJsExpression).replace("INPUT", "sig")
            val nExpression = requireNotNull(config.nJsExpression).replace("INPUT", "n")
            val exports =
                "; window._cipherSigFunc=function(sig){try{return $sigExpression;}catch(e){return null;}};" +
                    "window._nTransformFunc=function(n){try{return $nExpression;}catch(e){return n;}}; "

            return if (PLAYER_IIFE_TRAILER in playerCode) {
                playerCode.replace(PLAYER_IIFE_TRAILER, "$exports $PLAYER_IIFE_TRAILER")
            } else {
                "$playerCode\n$exports"
            }
        }
    }

    suspend fun solveSignature(input: String): String? = call("_cipherSigFunc", input)?.takeUnless { it.isBlank() }

    suspend fun solveN(input: String): String? = call("_nTransformFunc", input)?.takeUnless { it.isBlank() || it == input }

    private suspend fun call(
        functionName: String,
        input: String,
    ): String? =
        try {
            engine.callFunction(functionName, input)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    suspend fun dispose() = engine.dispose()
}
