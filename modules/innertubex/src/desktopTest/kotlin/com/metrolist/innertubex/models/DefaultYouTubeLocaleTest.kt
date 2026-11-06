package com.metrolist.innertubex.models

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultYouTubeLocaleTest {
    @Test
    fun preservesLanguageScriptAndRegion() {
        val locale =
            Locale
                .Builder()
                .setLanguage("zh")
                .setScript("Hant")
                .setRegion("TW")
                .build()

        assertEquals(YouTubeLocale(gl = "TW", hl = "zh-Hant-TW"), systemYouTubeLocale(locale))
    }

    @Test
    fun doesNotInventRegionWhenSystemLocaleHasNone() {
        assertEquals(YouTubeLocale(gl = "", hl = "it"), systemYouTubeLocale(Locale.forLanguageTag("it")))
    }
}
