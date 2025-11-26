package me.knighthat.kreate.theme

import android.content.res.Resources
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ThemeTest {

    @Test
    @Config(sdk = [
        Build.VERSION_CODES.M,
        Build.VERSION_CODES.N,
        Build.VERSION_CODES.N_MR1,
        Build.VERSION_CODES.O,
        Build.VERSION_CODES.O_MR1,
        Build.VERSION_CODES.P,
        Build.VERSION_CODES.Q,
        Build.VERSION_CODES.R
    ])
    fun testIsDynamicColorSupportedPre31() {
        assertFalse( isDynamicColorSupported() )
    }

    @Test
    @Config(sdk = [
        Build.VERSION_CODES.O,
        Build.VERSION_CODES.O_MR1,
        Build.VERSION_CODES.P,
        Build.VERSION_CODES.Q,
        Build.VERSION_CODES.R
    ])
    fun testGetDynamicColorSchemePre31() = runComposeUiTest {
        setContent {
            assertFailsWith( Resources.NotFoundException::class ) {
                getDynamicColorScheme(true)
            }
            assertFailsWith( Resources.NotFoundException::class ) {
                getDynamicColorScheme(false)
            }
        }
    }

    @Test
    @Config(sdk = [
        Build.VERSION_CODES.S,
        Build.VERSION_CODES.S_V2,
        Build.VERSION_CODES.TIRAMISU,
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        Build.VERSION_CODES.VANILLA_ICE_CREAM,
        Build.VERSION_CODES.BAKLAVA
    ])
    fun testIsDynamicColorSupportedPost31() {
        assertTrue( isDynamicColorSupported() )
    }

    @Test
    @Config(sdk = [
        Build.VERSION_CODES.M,
        Build.VERSION_CODES.N,
        Build.VERSION_CODES.N_MR1,
        Build.VERSION_CODES.S,
        Build.VERSION_CODES.S_V2,
        Build.VERSION_CODES.TIRAMISU,
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        Build.VERSION_CODES.VANILLA_ICE_CREAM,
        Build.VERSION_CODES.BAKLAVA
    ])
    fun testGetDynamicColorSchemePost31() = runComposeUiTest {
        setContent {
            val dynamicDarkColorScheme = getDynamicColorScheme(true)
            assertNotEquals( darkColorScheme(), dynamicDarkColorScheme )

            val dynamicLightColorScheme = getDynamicColorScheme( false )
            assertNotEquals( lightColorScheme(), dynamicLightColorScheme )
        }
    }
}