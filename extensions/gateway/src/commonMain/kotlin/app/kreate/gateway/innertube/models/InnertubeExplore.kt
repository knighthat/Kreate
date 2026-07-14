package app.kreate.gateway.innertube.models


interface InnertubeExplore {

    val newAlbumsAndSingles: Section?

    val moodsAndGenres: InnertubeMoodSection?

    val popularEpisodes: Section?

    val trending: Section?

    val newMusicVideos: Section?
}