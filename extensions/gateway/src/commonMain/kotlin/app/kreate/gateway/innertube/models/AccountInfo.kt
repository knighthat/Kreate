package app.kreate.gateway.innertube.models

import app.kreate.gateway.innertube.responses.Thumbnails


interface AccountInfo {

    val name: String
    val email: String?
    val channelHandle: String?
    val thumbnailUrl: List<Thumbnails.Thumbnail>
}