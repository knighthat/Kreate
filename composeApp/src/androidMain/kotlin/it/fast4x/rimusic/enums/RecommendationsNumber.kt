package it.fast4x.rimusic.enums

enum class RecommendationsNumber {
    `5`,
    `10`,
    `15`,
    `20`;

    fun toInt(): Int = this.name.toInt()
}
