package it.fast4x.rimusic.enums

enum class MaxStatisticsItems {
    `10`,
    `20`,
    `30`,
    `40`,
    `50`;

    fun toInt(): Int = this.name.toInt()

    fun toLong(): Long = this.name.toLong()
}