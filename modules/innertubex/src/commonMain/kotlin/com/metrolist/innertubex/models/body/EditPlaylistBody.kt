package com.metrolist.innertubex.models.body

import com.metrolist.innertubex.models.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
internal data class EditPlaylistBody(
    val context: Context,
    val playlistId: String,
    val actions: List<JsonObject>,
)

internal object Action {
    fun addVideoAction(addedVideoId: String): JsonObject =
        buildJsonObject {
            put("action", "ACTION_ADD_VIDEO")
            put("addedVideoId", addedVideoId)
        }

    fun addPlaylistAction(addedFullListId: String): JsonObject =
        buildJsonObject {
            put("action", "ACTION_ADD_PLAYLIST")
            put("addedFullListId", addedFullListId)
        }

    fun moveVideoAction(
        setVideoId: String,
        movedSetVideoIdSuccessor: String?,
    ): JsonObject =
        buildJsonObject {
            put("action", "ACTION_MOVE_VIDEO_BEFORE")
            put("setVideoId", setVideoId)
            put("movedSetVideoIdSuccessor", movedSetVideoIdSuccessor)
        }

    fun removeVideoAction(
        setVideoId: String,
        removedVideoId: String,
    ): JsonObject =
        buildJsonObject {
            put("action", "ACTION_REMOVE_VIDEO")
            put("setVideoId", setVideoId)
            put("removedVideoId", removedVideoId)
        }

    fun renamePlaylistAction(playlistName: String): JsonObject =
        buildJsonObject {
            put("action", "ACTION_SET_PLAYLIST_NAME")
            put("playlistName", playlistName)
        }

    fun setPlaylistDescriptionAction(playlistDescription: String): JsonObject =
        buildJsonObject {
            put("action", "ACTION_SET_PLAYLIST_DESCRIPTION")
            put("playlistDescription", playlistDescription)
        }

    fun setCustomThumbnailAction(encryptedBlobId: String): JsonObject =
        buildJsonObject {
            put("action", "ACTION_SET_CUSTOM_THUMBNAIL")
            put(
                "addedCustomThumbnail",
                buildJsonObject {
                    put("playlistScottyEncryptedBlobId", encryptedBlobId)
                },
            )
        }

    fun removeCustomThumbnailAction(): JsonObject =
        buildJsonObject {
            put("action", "ACTION_REMOVE_CUSTOM_THUMBNAIL")
        }
}
