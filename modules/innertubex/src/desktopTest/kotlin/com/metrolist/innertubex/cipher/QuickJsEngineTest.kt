package com.metrolist.innertubex.cipher

import com.dokar.quickjs.QuickJsException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class QuickJsEngineTest {
    @Test
    fun cancellationDuringInitializationDoesNotLoseTheCreatedRuntime() =
        runBlocking {
            val engine = QuickJsEngine()
            try {
                val initializing = launch(start = CoroutineStart.UNDISPATCHED) { engine.initialize() }
                initializing.cancelAndJoin()
                // Even if the caller is cancelled on the thread hop, ownership must be published.
                assertEquals("1", engine.evaluate("1", maxResultLength = 1))
            } finally {
                engine.dispose()
            }
        }

    @Test
    fun recursiveJavascriptFailsWithoutOverflowingTheNativeStack() =
        runBlocking {
            val engine = QuickJsEngine()
            try {
                engine.initialize()

                assertFailsWith<QuickJsException> {
                    engine.evaluate("(function recurse() { return recurse(); })()", maxResultLength = 1)
                }
                // Callers on other threads must still hit the limit measured on the runtime's own thread.
                List(8) {
                    async(Dispatchers.IO) {
                        assertFailsWith<QuickJsException> {
                            engine.evaluate("(function recurse() { return recurse(); })()", maxResultLength = 1)
                        }
                    }
                }.awaitAll()
                assertEquals("ok", engine.evaluate("'ok'", maxResultLength = 2))
            } finally {
                engine.dispose()
            }
        }

    @Test
    fun evaluatePreservesMultilineWhitespaceAndBoundsResults() =
        runBlocking {
            val engine = QuickJsEngine()
            try {
                engine.initialize()
                engine.execute("function oversized() { return 'x'.repeat(300000); }")

                val source = "  `  leading\n    \\\\ \" escaped`"
                assertEquals("  leading\n    \\ \" escaped", engine.evaluate(source, maxResultLength = 1024))
                assertNull(engine.callFunction("oversized", "input"))
                assertEquals("", engine.evaluate("'x'.repeat(300000)", maxResultLength = 1024))
            } finally {
                engine.dispose()
            }
        }
}
