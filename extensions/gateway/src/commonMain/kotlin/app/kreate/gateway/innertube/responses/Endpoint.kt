package app.kreate.gateway.innertube.responses


interface Endpoint {

    val browseEndpoint: Browse?
    val watchEndpoint: Watch?
    val watchPlaylistEndpoint: WatchPlaylist?
    val likeEndpoint: Like?
    val searchEndpoint: Search?
    val playlistEditEndpoint: PlaylistEdit?

    interface Browse {

        val browseId: String
        val params: String?
        val browseEndpointContextSupportedConfigs: Context?

        interface Context {

            val browseEndpointContextMusicConfig: MusicConfig

            interface MusicConfig {

                val pageType: String
            }
        }
    }

    interface Like {

        val status: String
        val target: Target

        interface Target {

            val playlistId: String
        }
    }

    interface PlaylistEdit {

        val playlistId: String
        val actions: List<Action>

        interface Action {

            val setVideoId: String
            val action: String
            val removedVideoId: String
        }
    }

    interface Search {

        val query: String
        val params: String?
    }

    interface Watch {

        val videoId: String
        val playlistId: String?
        val index: Int?
        val params: String?
        val playerParams: String?
        val ustreamerConfig: String?
        val playlistSetVideoId: String?
        val loggingContext: LoggingContext?
        val watchEndpointMusicSupportedConfigs: WatchEndpointMusicSupportedConfigs?

        interface LoggingContext {

            val vssLoggingContext: VssLoggingContext

            interface VssLoggingContext {

                val serializedContextData: String
            }
        }

        interface WatchEndpointMusicSupportedConfigs {
            val watchEndpointMusicConfig: WatchEndpointMusicConfig

            interface WatchEndpointMusicConfig {
                val hasPersistentPlaylistPanel: Boolean?

                val musicVideoType: String
            }
        }
    }

    interface WatchPlaylist {

        val playlistId: String?
        val params: String?
    }
}