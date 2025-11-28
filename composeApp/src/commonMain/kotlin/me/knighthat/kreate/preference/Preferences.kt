package me.knighthat.kreate.preference

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.NonBlocking
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject


abstract class Preferences<T> internal constructor(
    protected val storage: Storage,
    protected val oldKey: String,
    @field:PrefKey val key: String,
    val defaultValue: T
) : MutableStateFlow<T> {

    companion object {
        /**
         * Store general settings
         */
        private val preferences: Storage by inject(Storage::class.java, named(PrefType.PLAIN))

        /**
         * For logging credentials and other entries
         * that require total privacy.
         */
        private val credentials: Storage by inject(Storage::class.java, named(PrefType.PRIVATE))
    }

    protected val internalFlow: MutableStateFlow<T> = MutableStateFlow(
        value = getFromStorage() ?: defaultValue
    )

    override var value: T
        get() = internalFlow.value
        // Use tryEmit to write new value to persistent file
        set(value) { tryEmit( value ) }
    override val replayCache: List<T>
        get() = internalFlow.replayCache
    override val subscriptionCount: StateFlow<Int>
        get() = internalFlow.subscriptionCount

    /**
     * Extract value from [Storage].
     *
     * @return value of this preference, `null` if [key] doesn't exist
     */
    protected abstract fun getFromStorage(): T?

    /**
     * Write [value] into [Storage] instance.
     *
     * This is a non-blocking calls. Meaning, all writes
     * are temporary written to memory first, then sync
     * value to disk asynchronously.
     */
    @NonBlocking
    protected abstract fun apply( value: T )

    /**
     * Write [value] into [Storage] instance.
     *
     * This is a blocking calls. Meaning, changes
     * are written directly to persistent storage.
     */
    protected abstract suspend fun commit( value: T ): Boolean

    /**
     * Write [defaultValue] to this setting
     */
    fun reset() { tryEmit( defaultValue )  }

    override fun compareAndSet( expect: T, update: T ): Boolean =
        internalFlow.compareAndSet( expect, update )

    override suspend fun collect( collector: FlowCollector<T> ): Nothing =
        internalFlow.collect( collector )

    override suspend fun emit( value: T ) {
        this.commit( value )
        internalFlow.emit( value )
    }

    override fun tryEmit( value: T ): Boolean {
        this.apply( value )
        return internalFlow.tryEmit( value )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun resetReplayCache() = internalFlow.resetReplayCache()

    object Key {
    }
}