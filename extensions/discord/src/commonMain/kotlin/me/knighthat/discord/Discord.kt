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

    /**
     * Update rich presence to provided [song] activity
     */
    suspend fun listening( song: ListeningActivity )

    /**
     * Update rich presence to stop timeline and
     * switch to pausing (idling) state, while
     * still showing the [song].
     */
    suspend fun pause( song: ListeningActivity )

    /**
     * Reset presence to only show app's name and logo
     */
    suspend fun reset()
}