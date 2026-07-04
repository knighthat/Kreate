package app.kreate.widgets

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver


internal class CompactWidgetReceivers(
    override val glanceAppWidget: GlanceAppWidget = CompactWidget()
) : GlanceAppWidgetReceiver()

internal class TraditionalWidgetReceiver(
    override val glanceAppWidget: GlanceAppWidget = TraditionalWidget()
) : GlanceAppWidgetReceiver()