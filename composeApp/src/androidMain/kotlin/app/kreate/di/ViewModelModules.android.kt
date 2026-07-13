package app.kreate.di

import app.kreate.android.viewmodel.LocalPlaylistViewModel
import app.kreate.android.viewmodel.OnlineSearchViewModel
import app.kreate.android.viewmodel.SearchResultViewModel
import app.kreate.android.viewmodel.YouTubePlaylistViewModel
import app.kreate.android.viewmodel.YoutubeAlbumViewModel
import app.kreate.android.viewmodel.YoutubeArtistViewModel
import app.kreate.android.viewmodel.home.HomeAlbumsViewModel
import app.kreate.android.viewmodel.home.HomeArtistsViewModel
import app.kreate.android.viewmodel.home.HomeLibraryViewModel
import app.kreate.android.viewmodel.home.HomeQuickPicksViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module


actual val viewModelModule: Module = module {
    viewModelOf( ::YoutubeArtistViewModel )
    viewModelOf( ::YouTubePlaylistViewModel )
    viewModelOf( ::YoutubeAlbumViewModel )
    viewModelOf( ::LocalPlaylistViewModel )
    viewModelOf( ::HomeLibraryViewModel )
    viewModelOf( ::HomeAlbumsViewModel )
    viewModelOf( ::HomeArtistsViewModel )
    viewModelOf( ::HomeQuickPicksViewModel )
    viewModelOf( ::OnlineSearchViewModel )
    viewModelOf( ::SearchResultViewModel )
}