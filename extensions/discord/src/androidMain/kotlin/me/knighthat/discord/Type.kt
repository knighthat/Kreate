package me.knighthat.discord

import kizzy.gateway.entities.presence.Activity


/**
 * A set of pre-defined activity types.
 *
 * @see <a href='https://discord.com/developers/docs/events/gateway-events#activity-object-activity-types'>Activity types</a>
 */
object Type {

    /**
     * Format: Playing [Activity.name]
     *
     * Example: "Playing Rocket League"
     */
    const val PLAYING = 0

    /**
     * Currently only supports Twitch and YouTube. Only `https://twitch.tv/` and `https://youtube.com/` urls will work.
     *
     * Format: Streaming [Activity.details]
     *
     * Example: "Streaming Rocket League"
     */
    const val STREAMING = 1

    /**
     * Format: 	Listening to [Activity.name]
     *
     * Example: "Listening to Spotify"
     */
    const val LISTENING = 2

    /**
     * Format: Watching [Activity.name]
     *
     * Example: "Watching YouTube Together"
     */
    const val WATCHING = 3

    /**
     * Format: [Activity.state]
     *
     * Example: ":smiley: I am cool"
     */
    const val CUSTOM = 4

    /**
     * Format: Competing in [Activity.name]
     *
     * Example: "Competing in Arena World Champions"
     */
    const val COMPETING = 6
}
