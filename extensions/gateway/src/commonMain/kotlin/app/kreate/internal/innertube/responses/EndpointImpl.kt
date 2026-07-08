package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Endpoint
import kotlinx.serialization.Serializable


@Serializable
internal data class EndpointImpl(
    override val browseEndpoint: BrowseImpl?,
    override val watchEndpoint: WatchImpl?,
    override val watchPlaylistEndpoint: WatchPlaylistImpl?,
    override val likeEndpoint: LikeImpl?,
    override val searchEndpoint: SearchImpl?,
    override val playlistEditEndpoint: PlaylistEditImpl?,
): Endpoint {

    @Serializable
    internal data class BrowseImpl(
        override val browseId: String,
        override val params: String?,
        override val browseEndpointContextSupportedConfigs: ContextImpl?
    ): Endpoint.Browse {

        @Serializable
        internal data class ContextImpl(
            override val browseEndpointContextMusicConfig: MusicConfigImpl
        ): Endpoint.Browse.Context {

            @Serializable
            internal data class MusicConfigImpl(
                override val pageType: String
            ): Endpoint.Browse.Context.MusicConfig
        }
    }

    @Serializable
    internal data class LikeImpl(
        override val status: String,
        override val target: TargetImpl
    ): Endpoint.Like {

        @Serializable
        internal data class TargetImpl(
            override val playlistId: String
        ): Endpoint.Like.Target
    }

    @Serializable
    internal data class PlaylistEditImpl(
        override val playlistId: String,
        override val actions: List<ActionImpl> = emptyList()
    ): Endpoint.PlaylistEdit {

        @Serializable
        internal data class ActionImpl(
            override val setVideoId: String,
            override val action: String,
            override val removedVideoId: String
        ): Endpoint.PlaylistEdit.Action
    }

    @Serializable
    internal data class SearchImpl(
        override val query: String,
        override val params: String?
    ): Endpoint.Search

    @Serializable
    internal data class WatchImpl(
        override val videoId: String,
        override val playlistId: String?,
        override val index: Int?,
        override val params: String?,
        override val playerParams: String?,
        override val ustreamerConfig: String?,
        override val playlistSetVideoId: String?,
        override val loggingContext: LoggingContextImpl?,
        override val watchEndpointMusicSupportedConfigs: WatchEndpointMusicSupportedConfigsImpl?
    ): Endpoint.Watch {

        @Serializable
        internal data class LoggingContextImpl(
            override val vssLoggingContext: VssLoggingContextImpl
        ): Endpoint.Watch.LoggingContext {

            @Serializable
            internal data class VssLoggingContextImpl(
                override val serializedContextData: String
            ): Endpoint.Watch.LoggingContext.VssLoggingContext
        }

        @Serializable
        internal data class WatchEndpointMusicSupportedConfigsImpl(
            override val watchEndpointMusicConfig: WatchEndpointMusicConfigImpl
        ): Endpoint.Watch.WatchEndpointMusicSupportedConfigs {

            @Serializable
            internal data class WatchEndpointMusicConfigImpl(
                override val hasPersistentPlaylistPanel: Boolean?,
                override val musicVideoType: String
            ): Endpoint.Watch.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig
        }
    }

    @Serializable
    internal data class WatchPlaylistImpl(
        override val playlistId: String?,
        override val params: String?
    ): Endpoint.WatchPlaylist
}
