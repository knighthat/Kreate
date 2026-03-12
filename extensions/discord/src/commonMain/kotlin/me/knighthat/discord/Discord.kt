package me.knighthat.discord


interface Discord {

    /**
     * Start a new session with provided [token]
     *
     * Existing connection will be canceled before
     * new connection starts.
     *
     * @param token user's personal token
     */
    fun login( token: String )

    /**
     * Close connection to Discord.
     *
     * This will not release resources such as coroutine scopes,
     * network clients.
     *
     * Use this if user's switching account or wanting
     * to stop using DiscordRPC
     */
    suspend fun logout(): Boolean
}