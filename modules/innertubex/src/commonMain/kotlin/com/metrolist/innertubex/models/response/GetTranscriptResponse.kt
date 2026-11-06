package com.metrolist.innertubex.models.response

import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptResponse(
    val actions: List<Action>? = null,
) {
    @Serializable
    data class Action(
        val updateEngagementPanelAction: UpdateEngagementPanelAction? = null,
    )

    @Serializable
    data class UpdateEngagementPanelAction(
        val content: Content? = null,
    )

    @Serializable
    data class Content(
        val transcriptRenderer: TranscriptRenderer? = null,
    )

    @Serializable
    data class TranscriptRenderer(
        val body: Body? = null,
    )

    @Serializable
    data class Body(
        val transcriptBodyRenderer: TranscriptBodyRenderer? = null,
    )

    @Serializable
    data class TranscriptBodyRenderer(
        val cueGroups: List<CueGroup> = emptyList(),
    )

    @Serializable
    data class CueGroup(
        val transcriptCueGroupRenderer: TranscriptCueGroupRenderer? = null,
    )

    @Serializable
    data class TranscriptCueGroupRenderer(
        val cues: List<Cue> = emptyList(),
    )

    @Serializable
    data class Cue(
        val transcriptCueRenderer: TranscriptCueRenderer? = null,
    )

    @Serializable
    data class TranscriptCueRenderer(
        val cue: SimpleText? = null,
        val startOffsetMs: Long? = null,
        val durationMs: Long? = null,
    )

    @Serializable
    data class SimpleText(
        val simpleText: String = "",
    )
}
