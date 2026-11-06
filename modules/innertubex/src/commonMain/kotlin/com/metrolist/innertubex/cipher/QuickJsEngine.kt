package com.metrolist.innertubex.cipher

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wrapper around the QuickJS engine for executing JavaScript code.
 * This is used to run the YouTube player cipher deobfuscation logic.
 *
 * Based on yt-dlp's QuickJS integration approach.
 */
internal class QuickJsEngine {
    companion object {
        private const val EVALUATION_TIMEOUT_MS = 30_000L

        // Current ~2.6 MiB player scripts need more than 128 MiB while EJS builds their AST.
        private const val NATIVE_MEMORY_LIMIT_BYTES = 192L * 1024L * 1024L
        private const val MAX_STACK_SIZE_BYTES = 256L * 1024L
        private const val MAX_EVALUATION_RESULT_LENGTH = 16 * 1024 * 1024
        private const val MAX_FUNCTION_INPUT_LENGTH = 64 * 1024
        private const val MAX_FUNCTION_RESULT_LENGTH = 256 * 1024
        private val JS_IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

        /** Valid JavaScript double-quoted string literal for passing into evaluated calls. */
        internal fun jsStringLiteral(s: String): String =
            buildString(s.length + 2) {
                append('"')
                for (c in s) {
                    when (c) {
                        '\\' -> {
                            append("\\\\")
                        }

                        '"' -> {
                            append("\\\"")
                        }

                        '\n' -> {
                            append("\\n")
                        }

                        '\r' -> {
                            append("\\r")
                        }

                        '\t' -> {
                            append("\\t")
                        }

                        else -> {
                            if (c.code < 0x20) {
                                val hex = c.code.toString(16).padStart(4, '0')
                                append("\\u$hex")
                            } else {
                                append(c)
                            }
                        }
                    }
                }
                append('"')
            }
    }

    private class Runtime(
        val js: QuickJs,
        val thread: CloseableCoroutineDispatcher,
    )

    private val mutex = Mutex()
    private var runtime: Runtime? = null

    /**
     * Initialize the QuickJS runtime.
     *
     * QuickJS measures its stack limit from the native thread that created the runtime, while
     * its suspending evaluate API can resume on another worker of a shared pool. Each runtime
     * therefore gets one dedicated thread that creates, runs, and closes it.
     */
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    suspend fun initialize() =
        mutex.withLock {
            if (runtime != null) return@withLock
            // Keep publication non-cancellable too: returning from the dedicated thread can
            // otherwise discard the newly created runtime when the caller has been cancelled.
            withContext(NonCancellable) {
                val thread = newSingleThreadContext("InnerTubeX-QuickJS")
                try {
                    val js =
                        withContext(thread) {
                            QuickJs.create(thread).also {
                                it.evaluationTimeoutMillis = EVALUATION_TIMEOUT_MS
                                it.memoryLimit = NATIVE_MEMORY_LIMIT_BYTES
                                it.maxStackSize = MAX_STACK_SIZE_BYTES
                            }
                        }
                    runtime = Runtime(js, thread)
                } catch (e: Throwable) {
                    thread.close()
                    throw e
                }
            }
        }

    private suspend fun <T> withRuntime(block: suspend QuickJs.() -> T): T =
        mutex.withLock {
            val current = runtime ?: throw IllegalStateException("QuickJS not initialized")
            withContext(current.thread) { current.js.block() }
        }

    /**
     * Execute JavaScript code and return the result.
     *
     * @param code The JavaScript code to execute
     * @param collectGarbage Whether to collect unreachable cycles before returning
     * @return The result of the execution as a string
     */
    suspend fun evaluate(
        code: String,
        maxResultLength: Int,
        collectGarbage: Boolean = false,
    ): String {
        require(maxResultLength in 1..MAX_EVALUATION_RESULT_LENGTH) { "Invalid QuickJS result limit" }
        val boundedCode =
            """
            (function() {
              const value = ($code);
              if (value == null) return "";
              const text = String(value);
              return text.length <= $maxResultLength ? text : "";
            })()
            """
        return withRuntime {
            try {
                evaluate<String?>(boundedCode).orEmpty()
            } finally {
                if (collectGarbage) gc()
            }
        }
    }

    /** Execute JavaScript for side effects without asking QuickJS to marshal the final value. */
    suspend fun execute(code: String) {
        withRuntime { evaluate<Any?>("$code\n;undefined;") }
    }

    /**
     * Execute a JavaScript function with parameters.
     *
     * @param functionName The name of the function to call
     * @param input The string parameter to pass to the function
     * @return The bounded string result, or null when the input or result is too large
     */
    suspend fun callFunction(
        functionName: String,
        input: String,
    ): String? {
        require(JS_IDENTIFIER.matches(functionName)) { "Invalid JavaScript function name" }
        if (input.length > MAX_FUNCTION_INPUT_LENGTH) return null
        val inputLiteral = jsStringLiteral(input)
        return withRuntime {
            evaluate<String?>(
                """
                (function() {
                  const value = $functionName($inputLiteral);
                  if (value == null) return null;
                  const text = String(value);
                  return text.length <= $MAX_FUNCTION_RESULT_LENGTH ? text : null;
                })()
                """.trimIndent(),
            )
        }
    }

    /**
     * Set up the global environment for YouTube player execution.
     * This creates necessary globals like XMLHttpRequest, URL, location, etc.
     */
    suspend fun setupYoutubeGlobals() {
        val setupCode =
            """
            if (typeof globalThis.XMLHttpRequest === "undefined") {
                globalThis.XMLHttpRequest = { prototype: {} };
            }
            if (typeof URL === "undefined") {
                globalThis.location = {
                    hash: "",
                    host: "www.youtube.com",
                    hostname: "www.youtube.com",
                    href: "https://www.youtube.com/watch?v=yt-dlp-wins",
                    origin: "https://www.youtube.com",
                    password: "",
                    pathname: "/watch",
                    port: "",
                    protocol: "https:",
                    search: "?v=yt-dlp-wins",
                    username: "",
                };
            } else {
                globalThis.location = new URL("https://www.youtube.com/watch?v=yt-dlp-wins");
            }
            if (typeof globalThis.document === "undefined") {
                globalThis.document = Object.create(null);
            }
            if (typeof globalThis.navigator === "undefined") {
                globalThis.navigator = Object.create(null);
            }
            if (typeof globalThis.self === "undefined") {
                globalThis.self = globalThis;
            }
            if (typeof globalThis.window === "undefined") {
                globalThis.window = globalThis;
            }
            if (typeof globalThis.Intl === "undefined") {
                const NumberFormat = function(locale, options) {
                    this.options = options || {};
                };
                NumberFormat.supportedLocalesOf = function(locales) {
                    return Array.isArray(locales) ? locales : [locales];
                };
                NumberFormat.prototype.format = function(value) {
                    let formatted = String(value);
                    const minimumDigits = this.options.minimumIntegerDigits || 0;
                    while (formatted.length < minimumDigits) formatted = "0" + formatted;
                    return formatted;
                };
                const DateTimeFormat = function() {};
                DateTimeFormat.prototype.resolvedOptions = function() {
                    return { timeZone: "UTC" };
                };
                DateTimeFormat.prototype.format = function(value) {
                    return String(value);
                };
                globalThis.Intl = { NumberFormat, DateTimeFormat };
            }
            """.trimIndent()

        execute(setupCode)
    }

    /**
     * Clean up and release resources.
     */
    suspend fun dispose() =
        withContext(NonCancellable) {
            mutex.withLock {
                val current = runtime ?: return@withLock
                runtime = null
                try {
                    withContext(current.thread) { current.js.close() }
                } finally {
                    current.thread.close()
                }
            }
        }
}
