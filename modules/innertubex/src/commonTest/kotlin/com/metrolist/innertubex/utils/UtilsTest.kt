package com.metrolist.innertubex.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {
    @Test
    fun queryDecoderPreservesUnicode() {
        assertEquals("café ♪", decodeQueryComponent("café+%E2%99%AA"))
    }
}
