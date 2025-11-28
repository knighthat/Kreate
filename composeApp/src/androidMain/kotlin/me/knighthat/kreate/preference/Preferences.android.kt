package me.knighthat.kreate.preference

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration


actual typealias Storage = SharedPreferences

actual class BooleanPref actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: Boolean
) : Preferences<Boolean>(storage, oldKey, key, defaultValue) {

    actual constructor(storage: Storage, key: String, defaultValue: Boolean):
            this(storage, "", key, defaultValue)

    actual fun flip(): Boolean {
        tryEmit( !value )
        return value
    }

    override fun getFromStorage(): Boolean? {
        var fromFile: Boolean? = null

        /*
             Set [fromFile] to the value of [previousKey] if it's
             existed in the preferences file, then delete that key
             (for migration to new key)
         */
        if( oldKey.isNotBlank() && storage.contains( oldKey ) ) {
            fromFile = storage.getBoolean( oldKey, defaultValue )
            storage.edit( commit = true ) {
                remove( oldKey )

                // Add this value to new [key], otherwise, only old key
                // will be removed and new key is not added until next start
                // with default value
                fromFile.also { putBoolean( key, it ) }
            }
        }

        /*
             Set [fromFile] to the value of [key] if it's
             existed in the preferences file.

             Reasons for 2 separate steps are:
             - When both [key] and [previousKey] are existed
             in side the file, [previousKey] will be deleted
             while value of [key] is being used.
             - Or either 1 of the key will be used if only
             1 of them existed inside the file.
        */
        if( storage.contains( key ) )
            fromFile = storage.getBoolean( key, defaultValue )

        return fromFile
    }

    override fun apply( value: Boolean ) =
        storage.edit {
            putBoolean( key, value )
        }

    @SuppressLint("UseKtx")
    override suspend fun commit( value: Boolean ): Boolean =
        // KTX doesn't have a return value
        storage.edit()
               .putBoolean( key, value )
               .commit()
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
        var fromFile: Int? = null

        /*
             Set [fromFile] to the value of [previousKey] if it's
             existed in the preferences file, then delete that key
             (for migration to new key)
         */
        if( oldKey.isNotBlank() && storage.contains( oldKey ) ) {
            fromFile = storage.getInt( oldKey, defaultValue.hashCode() )
            storage.edit( commit = true ) {
                remove( oldKey )

                // Add this value to new [key], otherwise, only old key
                // will be removed and new key is not added until next start
                // with default value
                fromFile.also { putInt( key, it ) }
            }
        }

        /*
             Set [fromFile] to the value of [key] if it's
             existed in the preferences file.

             Reasons for 2 separate steps are:
             - When both [key] and [previousKey] are existed
             in side the file, [previousKey] will be deleted
             while value of [key] is being used.
             - Or either 1 of the key will be used if only
             1 of them existed inside the file.
        */
        if( storage.contains( key ) )
            fromFile = storage.getInt( key, defaultValue.hashCode() )

        return fromFile?.let( ::Color )
    }

    override fun apply( value: Color ) =
        storage.edit {
            putInt( key, value.hashCode() )
        }

    @SuppressLint("UseKtx")
    override suspend fun commit( value: Color ): Boolean =
        // KTX doesn't have a return value
        storage.edit()
               .putInt( key, value.hashCode() )
               .commit()
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
        var fromFile: String? = null

        /*
             Set [fromFile] to the value of [previousKey] if it's
             existed in the preferences file, then delete that key
             (for migration to new key)
         */
        if( oldKey.isNotBlank() && storage.contains( oldKey ) ) {
            fromFile = storage.getString( oldKey, null )
            storage.edit( commit = true ) {
                remove( oldKey )

                // Add this value to new [key], otherwise, only old key
                // will be removed and new key is not added until next start
                // with default value
                fromFile?.also { putString( key, it ) }
            }
        }

        /*
             Set [fromFile] to the value of [key] if it's
             existed in the preferences file.

             Reason for 2 separate steps is:
             - When both [key] and [previousKey] are existed
             in side the file, [previousKey] will be deleted
             while value of [key] is being used.
             - Or either 1 of the key will be used if only
             1 of them existed inside the file.
        */
        if( storage.contains( key ) )
            fromFile = storage.getString( key, null )

        return fromFile
    }

    override fun apply( value: String ) =
        storage.edit {
            putString( key, value )
        }

    @SuppressLint("UseKtx")
    override suspend fun commit( value: String ): Boolean =
        // KTX doesn't have a return value
        storage.edit()
               .putString( key, value )
               .commit()
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
        var fromFile: Int? = null

        /*
             Set [fromFile] to the value of [previousKey] if it's
             existed in the preferences file, then delete that key
             (for migration to new key)
         */
        if( oldKey.isNotBlank() && storage.contains( oldKey ) ) {
            fromFile = storage.getInt( oldKey, defaultValue )
            storage.edit( commit = true ) {
                remove( oldKey )

                // Add this value to new [key], otherwise, only old key
                // will be removed and new key is not added until next start
                // with default value
                fromFile.also { putInt( key, it ) }
            }
        }

        /*
             Set [fromFile] to the value of [key] if it's
             existed in the preferences file.

             Reasons for 2 separate steps are:
             - When both [key] and [previousKey] are existed
             in side the file, [previousKey] will be deleted
             while value of [key] is being used.
             - Or either 1 of the key will be used if only
             1 of them existed inside the file.
        */
        if( storage.contains( key ) )
            fromFile = storage.getInt( key, defaultValue )

        return fromFile
    }

    override fun apply( value: Int ) =
        storage.edit {
            putInt( key, value )
        }

    @SuppressLint("UseKtx")
    override suspend fun commit( value: Int ): Boolean =
        // KTX doesn't have a return value
        storage.edit()
               .putInt( key, value )
               .commit()
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
        var fromFile: Float? = null

        /*
             Set [fromFile] to the value of [previousKey] if it's
             existed in the preferences file, then delete that key
             (for migration to new key)
         */
        if( oldKey.isNotBlank() && storage.contains( oldKey ) ) {
            fromFile = storage.getFloat( oldKey, defaultValue )
            storage.edit( commit = true ) {
                remove( oldKey )

                // Add this value to new [key], otherwise, only old key
                // will be removed and new key is not added until next start
                // with default value
                fromFile.also { putFloat( key, it ) }
            }
        }

        /*
             Set [fromFile] to the value of [key] if it's
             existed in the preferences file.

             Reasons for 2 separate steps are:
             - When both [key] and [previousKey] are existed
             in side the file, [previousKey] will be deleted
             while value of [key] is being used.
             - Or either 1 of the key will be used if only
             1 of them existed inside the file.
        */
        if( storage.contains( key ) )
            fromFile = storage.getFloat( key, defaultValue )

        return fromFile
    }

    override fun apply( value: Float ) =
        storage.edit {
            putFloat( key, value )
        }

    @SuppressLint("UseKtx")
    override suspend fun commit( value: Float ): Boolean =
        // KTX doesn't have a return value
        storage.edit()
               .putFloat( key, value )
               .commit()
}

actual class EnumPref<E : Enum<E>> actual constructor(
    storage: Storage,
    oldKey: String,
    key: String,
    defaultValue: E
) : Preferences<E>(storage, oldKey, key, defaultValue) {

    actual constructor(
        storage: Storage, key: String, defaultValue: E):
            this(storage, "", key, defaultValue)

    override fun getFromStorage(): E? {
        var fromFile: String? = null

        /*
             Set [fromFile] to the value of [previousKey] if it's
             existed in the preferences file, then delete that key
             (for migration to new key)
         */
        if( oldKey.isNotBlank() && storage.contains( oldKey ) ) {
            fromFile = storage.getString( oldKey, null )
            storage.edit( commit = true ) {
                remove( oldKey )

                // Add this value to new [key], otherwise, only old key
                // will be removed and new key is not added until next start
                // with default value
                fromFile.also { putString( key, it ) }
            }
        }

        /*
             Set [fromFile] to the value of [key] if it's
             existed in the preferences file.

             Reasons for 2 separate steps are:
             - When both [key] and [previousKey] are existed
             in side the file, [previousKey] will be deleted
             while value of [key] is being used.
             - Or either 1 of the key will be used if only
             1 of them existed inside the file.
        */
        if( storage.contains( key ) )
            fromFile = storage.getString( key, null )

        return fromFile?.let { enumStr ->
            defaultValue.javaClass.enumConstants?.firstOrNull { it.name == enumStr }
        }
    }

    override fun apply( value: E ) =
        storage.edit {
            putString( key, value.name )
        }

    @SuppressLint("UseKtx")
    override suspend fun commit( value: E ): Boolean =
        // KTX doesn't have a return value
        storage.edit()
               .putString( key, value.name )
               .commit()
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
        var fromFile: Long? = null

        /*
             Set [fromFile] to the value of [previousKey] if it's
             existed in the preferences file, then delete that key
             (for migration to new key)
         */
        if( oldKey.isNotBlank() && storage.contains( oldKey ) ) {
            fromFile = storage.getLong( oldKey, defaultValue )
            storage.edit( commit = true ) {
                remove( oldKey )

                // Add this value to new [key], otherwise, only old key
                // will be removed and new key is not added until next start
                // with default value
                fromFile.also { putLong( key, it ) }
            }
        }

        /*
             Set [fromFile] to the value of [key] if it's
             existed in the preferences file.

             Reasons for 2 separate steps are:
             - When both [key] and [previousKey] are existed
             in side the file, [previousKey] will be deleted
             while value of [key] is being used.
             - Or either 1 of the key will be used if only
             1 of them existed inside the file.
        */
        if( storage.contains( key ) )
            fromFile = storage.getLong( key, defaultValue )

        return fromFile
    }

    override fun apply( value: Long ) =
        storage.edit {
            putLong( key, value )
        }

    @SuppressLint("UseKtx")
    override suspend fun commit( value: Long ): Boolean =
        // KTX doesn't have a return value
        storage.edit()
               .putLong( key, value )
               .commit()
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
        var fromFile: Long? = null

        /*
             Set [fromFile] to the value of [previousKey] if it's
             existed in the preferences file, then delete that key
             (for migration to new key)
         */
        if( oldKey.isNotBlank() && storage.contains( oldKey ) ) {
            fromFile = storage.getLong( oldKey, defaultValue.inWholeMilliseconds )
            storage.edit( commit = true ) {
                remove( oldKey )

                // Add this value to new [key], otherwise, only old key
                // will be removed and new key is not added until next start
                // with default value
                fromFile.also { putLong( key, it ) }
            }
        }

        /*
             Set [fromFile] to the value of [key] if it's
             existed in the preferences file.

             Reasons for 2 separate steps are:
             - When both [key] and [previousKey] are existed
             in side the file, [previousKey] will be deleted
             while value of [key] is being used.
             - Or either 1 of the key will be used if only
             1 of them existed inside the file.
        */
        if( storage.contains( key ) )
            fromFile = storage.getLong( key, defaultValue.inWholeMilliseconds )

        return fromFile?.toDuration( DurationUnit.MILLISECONDS )
    }

    override fun apply( value: Duration ) =
        storage.edit {
            putLong( key, value.inWholeMilliseconds )
        }

    @SuppressLint("UseKtx")
    override suspend fun commit( value: Duration ): Boolean =
        // KTX doesn't have a return value
        storage.edit()
               .putLong( key, value.inWholeMilliseconds )
               .commit()
}