package app.kreate.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.QualifierValue
import org.koin.core.scope.Scope
import org.koin.dsl.module


typealias InternalPreferences = Preferences
typealias Storage = DataStore<InternalPreferences>

private const val EXTENSION = "preferences_pb"         // Required by datastore-preferences
private const val PREFERENCES_DATASTORE = "preferences"
private const val CREDENTIALS_DATASTORE = "credentials"

internal expect fun Scope.getProfilePath(): Path

val datastoreModule = module {
    single( PrefType.DEFAULT, true ) {
        val path = getProfilePath().resolve( "${PREFERENCES_DATASTORE}.$EXTENSION" )
        PreferenceDataStoreFactory.createWithPath { path }
    }
    single( PrefType.CREDENTIALS ) {
        val path = getProfilePath().resolve( "${CREDENTIALS_DATASTORE}.$EXTENSION" )
        PreferenceDataStoreFactory.createWithPath { path }
    }
}

enum class PrefType : Qualifier {

    DEFAULT, CREDENTIALS, PROFILES;

    override val value: QualifierValue = name
}