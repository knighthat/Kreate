package me.knighthat.kreate.preference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@RunWith(RobolectricTestRunner::class)
class PreferencesTest {

    lateinit var keys: List<String>
    lateinit var entries: List<Preferences<*>>

    @BeforeTest
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val preferences = context.getSharedPreferences( "preferences", Context.MODE_PRIVATE )
        val credentialsPreferences = context.getSharedPreferences( "credentials", Context.MODE_PRIVATE )
        Preferences.load( preferences, credentialsPreferences )

        // Iterate over all properties declared in [Preferences.Key]
        keys = Preferences.Key::class.memberProperties
            .filter { property ->
                // Get all String `const`
                property.isConst && property.returnType.classifier == String::class
            }
            .map { property ->
                // property.getter.call(targetObject) gets the value of the property
                // We can safely cast it to String because we filtered the type
                property.getter.call() as String
            }
            .toList()

        entries = Preferences.Companion::class.memberProperties
            .filter { property ->
                property.returnType.isSubtypeOf( Preferences::class.starProjectedType)
            }
            .map { it.get(Preferences.Companion) as Preferences<*> }
            .toList()
    }


    /**
     * For each preference entry, their key must exists in [Preferences.Key]
     */
    @Test
    fun ensureConstantKeyExist() {
        assertTrue {
            entries.all { it.key in keys }
        }
    }

    /**
     * [Preferences.key] is not allowed to have duplicate.
     *
     * If 2 (or more) entries have the same key, it's prohibited.
     */
    @Test
    fun ensureKeyUnique() {
        val remainingKeys = keys.toMutableSet()

        for( entry in entries ) {
            val key = entry.key

            assertContains(remainingKeys, key, "Duplicate key $key")
            remainingKeys.remove( key )
        }
    }

    @Test
    fun ensureNoEmptyKey() {
        val entries = Preferences.Companion::class.memberProperties
            .filter { property ->
                property.returnType.isSubtypeOf( Preferences::class.starProjectedType)
            }
        for( entry in entries ) {
            val instance = entry.get(Preferences.Companion) as Preferences<*>
            assertFalse( instance.key.isBlank(), "${entry.name} has empty key" )
        }
    }
}