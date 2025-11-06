package app.kreate.constant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class LanguageTest {

    @Test
    fun allCaps() {
        Language.entries.forEach { l ->
            assertEquals( l.name, l.name.uppercase() )
        }
    }

    @Test
    fun lowercaseCode() {
        Language.entries.forEach { l ->
            assertEquals( l.code, l.code.lowercase() )
        }
    }

    @Test
    fun uppercaseRegion() {
        Language.entries.forEach { l ->
            assertEquals( l.region, l.region.uppercase() )
        }
    }

    @Test
    fun uniqueCodesAndRegions() {
        val existingCodes = HashSet<String>()

        Language.entries.forEach { l ->
            when( l ) {
                Language.SYSTEM -> {
                    // System's language is blank on both
                    assertTrue( l.code.isBlank() )
                    assertTrue( l.region.isBlank() )
                }
                else -> {
                    if( l === Language.INTERLINGUA ) {
                        assertFalse( l.code.isBlank() )
                        // Only empty region on Interlingua
                        assertTrue( l.region.isBlank() )
                    }

                    val languageCode = l.code + "_" + l.region
                    assertFalse { languageCode in existingCodes }
                    existingCodes.add( languageCode )
                }
            }
        }
    }

    // Language.languageName is Composable, not tested here
    // Language.displayName is Composable, not tested here
    // TODO: Make tests for toLocale, toTranslatorLanguage
}