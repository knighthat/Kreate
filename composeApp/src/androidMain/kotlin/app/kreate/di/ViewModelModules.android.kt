package app.kreate.di

import app.kreate.android.viewmodel.YouTubePlaylistViewModel
import app.kreate.android.viewmodel.YoutubeArtistViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module


actual val viewModelModule: Module = module {
    viewModelOf( ::YoutubeArtistViewModel )
    viewModelOf( ::YouTubePlaylistViewModel )
}