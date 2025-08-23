package me.knighthat.component.dialog

object InputDialogConstraints {

    /**
     * This REGEX pattern allows all characters
     */
    const val ALL = ".*";

    const val ANDROID_FILE_PATH = """^\/[\w\-]*(?:\/[\w\-\.]+)*$"""

    const val POSITIVE_DECIMAL = """^(\d+\.\d+|\d+\.|\.\d+|d+|\d+|\.|)$"""

    const val POSITIVE_INTEGER = """^\d*$"""
}