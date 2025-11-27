package me.knighthat.kreate.preference

import androidx.compose.ui.graphics.Color
import kotlin.time.Duration


actual interface Storage

actual class BooleanPref actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Boolean
) : Preferences<Boolean>(storage, oldKey, key, defaultValue) {

    actual constructor(storage: Storage, key: String, defaultValue: Boolean):
            this(storage, "", key, defaultValue)

    actual fun flip(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getFromStorage(): Boolean? {
        TODO("Not yet implemented")
    }

    override fun apply(value: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun commit(value: Boolean): Boolean {
        TODO("Not yet implemented")
    }
}

actual class ColorPref actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Color
) : Preferences<Color>(storage, oldKey, key, defaultValue) {

    actual constructor(storage: Storage, key: String, defaultValue: Color):
            this(storage, "", key, defaultValue)

    override fun getFromStorage(): Color? {
        TODO("Not yet implemented")
    }

    override fun apply(value: Color) {
        TODO("Not yet implemented")
    }

    override suspend fun commit(value: Color): Boolean {
        TODO("Not yet implemented")
    }
}

actual class StringPref actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: String
) : Preferences<String>(storage, oldKey, key, defaultValue) {

    actual constructor(storage: Storage, key: String, defaultValue: String):
            this(storage, "", key, defaultValue)

    override fun getFromStorage(): String? {
        TODO("Not yet implemented")
    }

    override fun apply(value: String) {
        TODO("Not yet implemented")
    }

    override suspend fun commit(value: String): Boolean {
        TODO("Not yet implemented")
    }
}

actual class IntPref actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Int
) : Preferences<Int>(storage, oldKey, key, defaultValue) {

    actual constructor(storage: Storage, key: String, defaultValue: Int):
            this(storage, "", key, defaultValue)

    override fun getFromStorage(): Int? {
        TODO("Not yet implemented")
    }

    override fun apply(value: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun commit(value: Int): Boolean {
        TODO("Not yet implemented")
    }
}

actual class FloatPref actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Float
) : Preferences<Float>(storage, oldKey, key, defaultValue) {

    actual constructor(storage: Storage, key: String, defaultValue: Float):
            this(storage, "", key, defaultValue)

    override fun getFromStorage(): Float? {
        TODO("Not yet implemented")
    }

    override fun apply(value: Float) {
        TODO("Not yet implemented")
    }

    override suspend fun commit(value: Float): Boolean {
        TODO("Not yet implemented")
    }
}

actual class EnumPref<E : Enum<E>> actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: E
) : Preferences<E>(storage, oldKey, key, defaultValue) {

    actual constructor(
        storage: Storage,
        key: String,
        defaultValue: E
    ) : this(storage, "", key, defaultValue)

    override fun getFromStorage(): E? {
        TODO("Not yet implemented")
    }

    override fun apply(value: E) {
        TODO("Not yet implemented")
    }

    override suspend fun commit(value: E): Boolean {
        TODO("Not yet implemented")
    }
}

actual class LongPref actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Long
) : Preferences<Long>(storage, oldKey, key, defaultValue) {

    actual constructor(storage: Storage, key: String, defaultValue: Long):
            this(storage, "", key, defaultValue)

    override fun getFromStorage(): Long? {
        TODO("Not yet implemented")
    }

    override fun apply(value: Long) {
        TODO("Not yet implemented")
    }

    override suspend fun commit(value: Long): Boolean {
        TODO("Not yet implemented")
    }
}

actual class DurationPref actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Duration
) : Preferences<Duration>(storage, oldKey, key, defaultValue) {

    actual constructor(storage: Storage, key: String, defaultValue: Duration):
            this(storage, "", key, defaultValue)

    override fun getFromStorage(): Duration? {
        TODO("Not yet implemented")
    }

    override fun apply(value: Duration) {
        TODO("Not yet implemented")
    }

    override suspend fun commit(value: Duration): Boolean {
        TODO("Not yet implemented")
    }
}