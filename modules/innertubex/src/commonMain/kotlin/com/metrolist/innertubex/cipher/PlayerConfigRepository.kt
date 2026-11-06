package com.metrolist.innertubex.cipher

interface PlayerConfigRepository {
    val enabled: Boolean
    val sourceUrl: String
    val defaultSourceUrl: String

    var cachedJson: String
    var cachedAtMs: Long
    var cachedSourceUrl: String
    var cachedEtag: String

    companion object {
        /** Creates a non-persistent repository with remote config loading disabled. */
        fun disabled(): PlayerConfigRepository =
            object : PlayerConfigRepository {
                override val enabled: Boolean = false
                override val sourceUrl: String = ""
                override val defaultSourceUrl: String = ""
                override var cachedJson: String = ""
                override var cachedAtMs: Long = 0L
                override var cachedSourceUrl: String = ""
                override var cachedEtag: String = ""
            }
    }
}
