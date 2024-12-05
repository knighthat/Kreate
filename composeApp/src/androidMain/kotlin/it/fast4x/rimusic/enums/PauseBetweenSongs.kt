package it.fast4x.rimusic.enums

enum class PauseBetweenSongs {
    `0`,
    `5`,
    `10`,
    `15`,
    `20`,
    `30`,
    `40`,
    `50`,
    `60`;

    val asSeconds: Int = this.name.toInt()

    val asMillis: Long = this.asSeconds * 1000L
}
