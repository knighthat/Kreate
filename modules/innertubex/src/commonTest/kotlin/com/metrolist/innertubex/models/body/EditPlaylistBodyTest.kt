package com.metrolist.innertubex.models.body

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class EditPlaylistBodyTest {
    @Test
    fun setPlaylistDescriptionActionUsesExpectedMutationPayload() {
        val action = Action.setPlaylistDescriptionAction("A description")

        assertEquals("ACTION_SET_PLAYLIST_DESCRIPTION", action["action"]?.jsonPrimitive?.content)
        assertEquals("A description", action["playlistDescription"]?.jsonPrimitive?.content)
    }

    @Test
    fun playlistAndThumbnailActionsUseExpectedMutationPayloads() {
        val addPlaylist = Action.addPlaylistAction("PL123")
        val setThumbnail = Action.setCustomThumbnailAction("blob-id")
        val removeThumbnail = Action.removeCustomThumbnailAction()

        assertEquals("ACTION_ADD_PLAYLIST", addPlaylist["action"]?.jsonPrimitive?.content)
        assertEquals("PL123", addPlaylist["addedFullListId"]?.jsonPrimitive?.content)
        assertEquals("ACTION_SET_CUSTOM_THUMBNAIL", setThumbnail["action"]?.jsonPrimitive?.content)
        assertEquals(
            "blob-id",
            setThumbnail["addedCustomThumbnail"]
                ?.jsonObject
                ?.get("playlistScottyEncryptedBlobId")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("ACTION_REMOVE_CUSTOM_THUMBNAIL", removeThumbnail["action"]?.jsonPrimitive?.content)
    }
}
