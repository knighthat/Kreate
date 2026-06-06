package app.kreate.preferences

import androidx.datastore.preferences.core.edit
import app.kreate.di.PrefType
import app.kreate.di.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
sealed class Preferences<K, V> protected constructor(
    protected val storage: Storage,
    @param:PrefKey val keyName: String,
    val defaultValue: V
) : MutableStateFlow<V> {

    companion object : KoinComponent {

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val preferences: Storage by inject(PrefType.DEFAULT)
    }

    protected abstract val key: androidx.datastore.preferences.core.Preferences.Key<K>

    private val _internalState = MutableStateFlow(defaultValue)

    override val replayCache: List<V> get() = _internalState.replayCache
    override val subscriptionCount: StateFlow<Int> get() = _internalState.subscriptionCount

    override var value: V
        get() = _internalState.value
        set(value) {
            _internalState.value = value
            scope.launch { writeToDisk(value) }
        }

    init {
        // Sync from Disk to the internal StateFlow
        scope.launch {
            storage.data
                   .mapNotNull {
                       it[key]?.let( ::deserialize )
                   }
                   .distinctUntilChanged()
                   .collect { _internalState.value = it }
        }
    }

    protected abstract fun deserialize( key: K ): V?

    protected abstract fun serialize( value: V ): K

    protected open suspend fun writeToDisk( value: V ) {
        storage.edit { it[key] = serialize(value) }
    }

    override suspend fun emit( value: V ) {
        _internalState.emit( value )
        writeToDisk( value )
    }

    override fun tryEmit( value: V ): Boolean {
        val success = _internalState.tryEmit( value )
        if (success) {
            scope.launch { writeToDisk(value) }
        }
        return success
    }

    override fun compareAndSet( expect: V, update: V ): Boolean {
        val success = _internalState.compareAndSet( expect, update )
        if (success) {
            scope.launch { writeToDisk(update) }
        }
        return success
    }

    override suspend fun collect( collector: FlowCollector<V> ): Nothing =
        _internalState.collect( collector )

    @ExperimentalCoroutinesApi
    override fun resetReplayCache() = _internalState.resetReplayCache()

    object Key {
    }
}