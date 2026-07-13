package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Button
import kotlinx.serialization.Serializable


@Serializable
internal data class ButtonImpl(
    override val subscribeButtonRenderer: SubscribeImpl?
): Button {

    @Serializable
    internal data class SubscribeImpl(
        override val subscriberCountText: RunsImpl,
        override val subscribed: Boolean?,
        override val enabled: Boolean?,
        override val type: String?,
        override val channelId: String,
        override val showPreference: Boolean?,
        override val subscriberCountWithSubscribeText: RunsImpl?,
        override val subscribedButtonText: RunsImpl?,
        override val unsubscribedButtonText: RunsImpl?,
        override val unsubscribeButtonText: RunsImpl?,
        override val longSubscriberCountText: RunsImpl,
        override val shortSubscriberCountText: RunsImpl,
        override val subscribeAccessibility: AccessibilityImpl?,
        override val unsubscribeAccessibility: AccessibilityImpl?,
    ): Button.Subscribe
}
