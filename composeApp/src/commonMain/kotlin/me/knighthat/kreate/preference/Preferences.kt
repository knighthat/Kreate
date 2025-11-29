package me.knighthat.kreate.preference

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import androidx.datastore.preferences.core.Preferences as Pref


typealias PrefDataStore = DataStore<Pref>

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
sealed class Preferences<K, V> protected constructor(
    protected val storage: PrefDataStore,
    @field:PrefKey val key: String,
    val defaultValue: V
) : StateFlow<V>, FlowCollector<V> {

    companion object : KoinComponent {

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        /**
         * Store general settings
         */
        private val preferences by inject<PrefDataStore>(named( PrefType.PLAIN ))

        /**
         * For logging credentials and other entries
         * that require total privacy.
         */
        private val credentials by inject<PrefDataStore>(named( PrefType.PRIVATE ))

        //<editor-fold desc="Runtime logs">
        val RUNTIME_LOG_FILE_SIZE by lazy {
            LongPref(preferences, Key.RUNTIME_LOG_FILE_SIZE, 5L * 1024 * 1024)      // Default: 5MB
        }
        val RUNTIME_LOG_NUM_FILES by lazy {
            IntPref(preferences, Key.RUNTIME_LOG_NUM_OF_FILES, 3)
        }
        val RUNTIME_LOG_SEVERITY by lazy {
            EnumPref(preferences, Key.RUNTIME_LOG_SEVERITY, Severity.Info)
        }
        //</editor-fold>
    }

    protected open val state: StateFlow<V> =
        storage.data
               .mapNotNull {
                   val fromFile = it[prefKey]
                   fromFile?.let( ::deserialize )
               }
               .stateIn(scope, SharingStarted.Eagerly, defaultValue)

    protected abstract val prefKey: Pref.Key<K>

    protected abstract fun deserialize( key: K ): V?

    protected abstract fun serialize( value: V ): K

    override val replayCache: List<V>
        get() = state.replayCache

    override var value: V
        get() = state.value
        set(value) {
            scope.launch { emit( value ) }
        }

    override suspend fun emit( value: V ) {
        storage.edit { prefs ->
            prefs[prefKey] = serialize( value )
        }
    }

    override suspend fun collect( collector: FlowCollector<V> ): Nothing = state.collect( collector )

    class StringPref(
        storage: PrefDataStore,
        key: String,
        defaultValue: String
    ) : Preferences<String, String>(storage, key, defaultValue) {

        override val prefKey = stringPreferencesKey( key )

        override fun deserialize( key: String ): String = key

        override fun serialize( value: String ): String = value
    }

    class IntPref(
        storage: PrefDataStore,
        key: String,
        defaultValue: Int
    ) : Preferences<Int, Int>(storage, key, defaultValue) {

        override val prefKey = intPreferencesKey( key )

        override fun deserialize( key: Int ): Int = key

        override fun serialize( value: Int ): Int = value
    }

    class FloatPref(
        storage: PrefDataStore,
        key: String,
        defaultValue: Float
    ) : Preferences<Float, Float>(storage, key, defaultValue) {

        override val prefKey = floatPreferencesKey( key )

        override fun deserialize( key: Float ): Float = key

        override fun serialize( value: Float ): Float = value
    }

    class LongPref(
        storage: PrefDataStore,
        key: String,
        defaultValue: Long
    ) : Preferences<Long, Long>(storage, key, defaultValue) {

        override val prefKey = longPreferencesKey( key )

        override fun deserialize( key: Long ): Long = key

        override fun serialize( value: Long ): Long = value
    }

    class BooleanPref(
        storage: PrefDataStore,
        key: String,
        defaultValue: Boolean
    ) : Preferences<Boolean, Boolean>(storage, key, defaultValue) {

        override val prefKey = booleanPreferencesKey( key )

        override fun deserialize( key: Boolean ): Boolean = key

        override fun serialize( value: Boolean ): Boolean = value

        fun flip() { value = !value }
    }

    class ColorPref(
        storage: PrefDataStore,
        key: String,
        defaultValue: Color
    ) : Preferences<Int, Color>(storage, key, defaultValue) {

        override val prefKey = intPreferencesKey( key )

        override fun deserialize( key: Int ): Color = Color(key)

        override fun serialize( value: Color ): Int = value.hashCode()
    }

    class DurationPref(
        storage: PrefDataStore,
        key: String,
        defaultValue: Duration
    ) : Preferences<Long, Duration>(storage, key, defaultValue) {

        override val prefKey = longPreferencesKey( key )

        override fun deserialize( key: Long ): Duration = key.toDuration( DurationUnit.MILLISECONDS )

        override fun serialize( value: Duration ): Long = value.inWholeMilliseconds
    }

    class EnumPref<E: Enum<E>>(
        storage: PrefDataStore,
        key: String,
        defaultValue: E
    ) : Preferences<String, E>(storage, key, defaultValue) {

        override val prefKey = stringPreferencesKey( key )

        override fun deserialize( key : String ): E? =
            defaultValue.javaClass
                        .enumConstants      // Use [defaultValue] to get all enum values of that enum class
                        ?.firstOrNull { it.name.equals( key, true ) }

        override fun serialize( value: E ): String = value.name
    }

    object Key {

        const val RUNTIME_LOG_FILE_SIZE = "runtime_log_file_size"
        const val RUNTIME_LOG_NUM_OF_FILES = "runtime_log_num_of_files"
        const val RUNTIME_LOG_SEVERITY = "runtime_log_severity"
    }
}