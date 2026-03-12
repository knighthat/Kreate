package me.knighthat.discord

import android.net.Uri


actual class ListeningActivity(
    val timeStart: Long,
    val duration: Long,
    val songName: String,
    val thumbnailUrl: Uri?,
    val artistName: String,
    val artistUrl: String?,
    val artistThumbnailUrl: Uri?,
    val albumName: String
)