package me.knighthat.kreate.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object PreferenceModule {

    private const val PREFERENCES_FILENAME = "preferences"
    private const val CREDENTIALS_FILENAME = "credentials"

    @Named("plain")
    @Provides
    @Singleton
    fun providePlainPreferences( @ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences( PREFERENCES_FILENAME, Context.MODE_PRIVATE )

    @Named("private")
    @Provides
    @Singleton
    fun providesPrivatePreferences( @ApplicationContext context: Context ): SharedPreferences =
        context.getSharedPreferences( CREDENTIALS_FILENAME, Context.MODE_PRIVATE )
}