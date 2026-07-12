package com.kickpredict.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Host-facing receiver that binds the home-screen widget to [KickPredictWidget]. */
class KickPredictWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KickPredictWidget()
}
