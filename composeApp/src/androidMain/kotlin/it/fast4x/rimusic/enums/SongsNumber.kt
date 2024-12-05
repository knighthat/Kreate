package it.fast4x.rimusic.enums

enum class SongsNumber {
    `1`,
    `2`,
    `3`,
    `4`,
    `5`,
    `6`,
    `7`,
    `8`,
    `9`;

    fun toInt(): Int = this.name.toInt()
}