package me.knighthat.component.dialog

object InputDialogConstraints {

    /**
     * This REGEX pattern allows all characters
     */
    const val ALL = ".*";

    const val ANDROID_FILE_PATH = """^\/[\w\-]*(?:\/[\w\-\.]+)*$"""

    const val POSITIVE_DECIMAL = """^(\d+\.\d+|\d+\.|\.\d+|d+|\d+|\.|)$"""

    const val POSITIVE_INTEGER = """^\d*$"""

    /**
     * Matches number from 0 to 50 inclusively. Empty is allowed
     */
    const val THUMBNAIL_ROUNDNESS_PERCENT = """^(50|[1-4]?[0-9]|)$"""

    const val ALBUM_THUMBNAIL_SIZE = """^1[0-6][0-9]$|^170$"""

    const val SONG_THUMBNAIL_SIZE = """^[3-7][0-9]$|^80$"""
}