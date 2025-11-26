package me.knighthat.kreate.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse


class ThemeTest {

    @Test
    fun testIsDynamicColorSupported() {
        assertFalse( isDynamicColorSupported() )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGetDynamicColorScheme() = runComposeUiTest {
        assertFailsWith( UnsupportedOperationException::class ) {
            setContent { getDynamicColorScheme( false ) }
        }
    }
}