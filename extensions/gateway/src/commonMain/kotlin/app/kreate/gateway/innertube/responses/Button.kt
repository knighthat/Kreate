package app.kreate.gateway.innertube.responses


interface Button {

    val subscribeButtonRenderer: Subscribe?

    interface Subscribe {

        val subscriberCountText: Runs
        val subscribed: Boolean?
        val enabled: Boolean?
        val type: String?
        val channelId: String
        val showPreference: Boolean?
        val subscriberCountWithSubscribeText: Runs?
        val subscribedButtonText: Runs?
        val unsubscribedButtonText: Runs?
        val unsubscribeButtonText: Runs?
        val longSubscriberCountText: Runs
        val shortSubscriberCountText: Runs
        val subscribeAccessibility: Accessibility?
        val unsubscribeAccessibility: Accessibility?
    }
}