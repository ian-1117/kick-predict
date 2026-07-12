package com.kickpredict.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kickpredict.R
import com.kickpredict.domain.model.MatchNotification
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.presentation.MainActivity

/** Builds and posts the localized system notifications for [MatchNotification] events. */
class MatchNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** Create the channel (idempotent). Safe to call before every post. */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    fun post(notification: MatchNotification) {
        if (!hasPermission()) return
        val (title, body) = render(notification)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        manager.notify(notification.key.hashCode(), builder.build())
    }

    private fun render(n: MatchNotification): Pair<String, String> = when (n) {
        is MatchNotification.Kickoff -> {
            val pick = pickLabel(n.pickedOutcome, n.home, n.away)
            val base = "${n.home} vs ${n.away} · $pick"
            val body = if (n.edge != null) "$base · ${context.getString(R.string.notif_value_suffix, n.edge)}" else base
            context.getString(R.string.notif_kickoff_title, n.minutesToKickoff) to body
        }
        is MatchNotification.Live ->
            context.getString(R.string.notif_live_title) to "${n.home} ${n.scoreline} ${n.away}"
        is MatchNotification.Result -> {
            val title = context.getString(
                if (n.hit) R.string.notif_result_hit else R.string.notif_result_miss,
            )
            title to "${n.home} ${n.scoreline} ${n.away}"
        }
    }

    private fun pickLabel(outcome: PredictedOutcome, home: String, away: String): String = when (outcome) {
        PredictedOutcome.HOME_WIN -> context.getString(R.string.outcome_win, home)
        PredictedOutcome.AWAY_WIN -> context.getString(R.string.outcome_win, away)
        PredictedOutcome.DRAW -> context.getString(R.string.outcome_draw)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "match_alerts"
    }
}
