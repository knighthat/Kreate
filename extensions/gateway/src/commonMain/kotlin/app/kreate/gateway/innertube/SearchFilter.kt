package app.kreate.gateway.innertube


@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class SearchFilter {

    companion object {

        const val SONGS = "EgWKAQIIAWoOEAQQAxAFEAoQCRAVEBA%3D"
        const val ALBUMS = "EgWKAQIYAWoOEAQQAxAFEAoQCRAVEBA%3D"
        const val ARTISTS = "EgWKAQIgAWoOEAQQAxAFEAoQCRAVEBA%3D"
        const val VIDEOS = "EgWKAQIIAWoOEAQQAxAFEAoQCRAVEBA%3D"
        const val COMMUNITY_PLAYLISTS = "EgeKAQQoAEABag4QAxAEEAkQChAVEBAQEQ%3D%3D"
        const val FEATURED_PLAYLISTS = "EgeKAQQoADgBahIQBBADEAUQCRAKEBUQDhAREBA%3D"
        const val PODCASTS = "EgWKAQJQAWoOEAMQBBAJEAoQFRAQEBE%3D"
    }
}