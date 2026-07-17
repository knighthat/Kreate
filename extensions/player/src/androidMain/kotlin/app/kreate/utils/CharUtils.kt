package app.kreate.utils


object CharUtils {

    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    fun randomString( length: Int ): String =
        (1..length).map { CHARS.random() }.joinToString("")
}