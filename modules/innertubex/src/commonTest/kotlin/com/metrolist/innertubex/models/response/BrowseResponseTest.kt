package com.metrolist.innertubex.models.response

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowseResponseTest {
    @Test
    fun decodesResponsivePlaylistHeaderAndMicroformatDescription() {
        val response =
            Json.decodeFromString<BrowseResponse>(
                """
                {
                  "header": {
                    "musicResponsiveHeaderRenderer": {
                      "description": {"runs": [{"text": "Header description"}]}
                    }
                  },
                  "microformat": {
                    "microformatDataRenderer": {"description": "Microformat description"}
                  }
                }
                """.trimIndent(),
            )

        val headerDescription =
            response.header
                ?.musicResponsiveHeaderRenderer
                ?.get("description")
                ?.jsonObject
                ?.get("runs")
                ?.jsonArray
                ?.first()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content
        val microformatDescription =
            response.microformat
                ?.get("microformatDataRenderer")
                ?.jsonObject
                ?.get("description")
                ?.jsonPrimitive
                ?.content

        assertEquals("Header description", headerDescription)
        assertEquals("Microformat description", microformatDescription)
    }
}
