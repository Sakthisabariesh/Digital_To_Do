package com.mindecho.app.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver responsible for managing MindEcho Glance AppWidget updates and lifecycle.
 */
class MindEchoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MindEchoGlanceWidget()
}
