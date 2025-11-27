package me.knighthat.kreate.preference

import androidx.compose.ui.graphics.Color
import kotlin.time.Duration


expect interface Storage

expect class BooleanPref(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Boolean
): Preferences<Boolean> {

    constructor(storage: Storage, key: String, defaultValue: Boolean)

    fun flip(): Boolean
}

expect class ColorPref(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Color
): Preferences<Color> {

    constructor(storage: Storage, key: String, defaultValue: Color)
}

expect class StringPref(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: String
): Preferences<String> {

    constructor(storage: Storage, key: String, defaultValue: String)
}

expect class IntPref(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Int
): Preferences<Int> {

    constructor(storage: Storage, key: String, defaultValue: Int)
}

expect class FloatPref(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Float
): Preferences<Float> {

    constructor(storage: Storage, key: String, defaultValue: Float)
}

expect class EnumPref<E: Enum<E>>(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: E
): Preferences<E> {

    constructor(storage: Storage, key: String, defaultValue: E)
}

expect class LongPref(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Long
): Preferences<Long> {

    constructor(storage: Storage, key: String, defaultValue: Long)
}

expect class DurationPref(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Duration
): Preferences<Duration> {

    constructor(storage: Storage, key: String, defaultValue: Duration)
}