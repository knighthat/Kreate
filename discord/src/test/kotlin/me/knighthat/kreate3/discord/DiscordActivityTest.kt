/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.discord

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DiscordActivityTest {

    @Test
    fun `texts fit what Discord takes`() {
        assertNull(fit("  "))
        assertEquals("A⠀", fit("A"))
        assertEquals(128, fit("x".repeat(200))!!.length)
        assertEquals("Song", fit(" Song "))
    }

    @Test
    fun `buttons go apart from their addresses`() {
        val json = DiscordActivity(
            name = "Kreate",
            type = ActivityType.LISTENING,
            details = "Song",
            buttons = listOf(
                ActivityButton("Play on YouTube Music", "https://music.youtube.com/watch?v=x"),
                ActivityButton("Get Kreate", "https://github.com/knighthat/Kreate"),
                ActivityButton("Third", "https://example.com"),
            ),
        ).toJson(large = "mp:external/a", small = null, appId = "1")

        assertEquals(2, json["type"]!!.jsonPrimitive.content.toInt())
        assertEquals("1", json["application_id"]!!.jsonPrimitive.content)
        assertEquals(2, json["buttons"]!!.jsonArray.size)
        assertEquals(
            "https://music.youtube.com/watch?v=x",
            json["metadata"]!!.jsonObject["button_urls"]!!.jsonArray[0].jsonPrimitive.content,
        )
        val assets = json["assets"] as JsonObject
        assertEquals("mp:external/a", assets["large_image"]!!.jsonPrimitive.content)
        assertFalse("small_image" in assets)
    }

    @Test
    fun `no timestamps without a start or an end`() {
        val json =
            DiscordActivity(name = "Kreate", type = ActivityType.PLAYING).toJson(null, null, "1")
        assertFalse("timestamps" in json)
        assertFalse("assets" in json)
        assertFalse("buttons" in json)
    }
}
