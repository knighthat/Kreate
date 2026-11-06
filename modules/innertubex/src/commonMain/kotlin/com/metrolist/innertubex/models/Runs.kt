package com.metrolist.innertubex.models

import kotlinx.serialization.Serializable

private val RUN_SEPARATOR_REGEX = Regex("[&,]")

@Serializable
data class Runs(
    val runs: List<Run>? = null,
)

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint? = null,
    val bold: Boolean? = null,
    val italics: Boolean? = null,
)

@Serializable
data class NavigationEndpoint(
    val browseEndpoint: BrowseEndpoint? = null,
    val watchEndpoint: WatchEndpoint? = null,
    val watchPlaylistEndpoint: WatchPlaylistEndpoint? = null,
)

@Serializable
data class BrowseEndpoint(
    val browseId: String,
    val params: String? = null,
)

@Serializable
data class WatchEndpoint(
    val videoId: String,
    val playlistId: String? = null,
    val index: Int? = null,
    val params: String? = null,
)

@Serializable
data class WatchPlaylistEndpoint(
    val playlistId: String,
    val params: String? = null,
)

fun List<Run>.splitBySeparator(): List<List<Run>> {
    val res = mutableListOf<List<Run>>()
    var tmp = mutableListOf<Run>()
    forEach { run ->
        if (run.text == " • ") {
            res.add(tmp)
            tmp = mutableListOf()
        } else {
            tmp.add(run)
        }
    }
    res.add(tmp)
    return res
}

fun List<List<Run>>.clean(): List<List<Run>> =
    if (getOrNull(0)?.getOrNull(0)?.navigationEndpoint != null ||
        (getOrNull(0)?.getOrNull(0)?.text?.contains(RUN_SEPARATOR_REGEX)) == true
    ) {
        this
    } else {
        this.drop(1)
    }

fun List<Run>.oddElements() =
    filterIndexed { index, _ ->
        index % 2 == 0
    }
