package com.metrolist.innertubex.extraction

public interface YtConfigParser {
    /** Sends login cookies only to the validated YouTube watch/embed page. */
    public suspend fun fetchConfig(
        videoId: String,
        useLoginCookies: Boolean = false,
    ): PlayerConfig

    public suspend fun fetchEmbeddedConfig(
        videoId: String,
        useLoginCookies: Boolean = false,
    ): PlayerConfig = fetchConfig(videoId, useLoginCookies)
}
