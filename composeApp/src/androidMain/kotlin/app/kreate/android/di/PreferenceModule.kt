package app.kreate.android.di

import android.content.Context
import android.content.SharedPreferences
import app.kreate.android.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object PreferenceModule {

    @Provides
    @Singleton
    fun provideSharedPreferences( @ApplicationContext context: Context ): SharedPreferences {
        Preferences.load( context )
        return Preferences.preferences
    }
}