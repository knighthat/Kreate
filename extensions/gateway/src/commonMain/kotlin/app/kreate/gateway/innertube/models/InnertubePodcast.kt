package app.kreate.gateway.innertube.models

import app.kreate.gateway.innertube.responses.Runs


interface InnertubePodcast : InnertubePlaylist {

    interface Item : InnertubeSong {

        val description: Runs
    }
}