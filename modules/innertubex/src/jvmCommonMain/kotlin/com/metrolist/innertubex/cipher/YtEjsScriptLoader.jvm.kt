package com.metrolist.innertubex.cipher

import kotlin.text.Charsets

private object YtEjsResourceAnchor

internal actual fun readYtEjsSolverScript(fileName: String): String {
    val path = "yt_ejs/$fileName"
    val stream =
        Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: YtEjsResourceAnchor.javaClass.getResourceAsStream("/$path")
            ?: YtEjsResourceAnchor.javaClass.getResourceAsStream(path)
    requireNotNull(stream) { "Missing classpath resource: $path" }
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
