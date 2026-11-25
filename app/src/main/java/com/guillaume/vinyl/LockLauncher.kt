package com.guillaume.vinyl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings

private const val CHANNEL = "lock"
const val LOCK_NOTIF_ID = 1

/**
 * Opens the lock screen player. Two routes, because phones differ:
 * - "display over other apps" granted: start it directly (most reliable on Samsung);
 * - otherwise a full-screen notification, the standard way to show something on the lock screen.
 */
fun launchLockPlayer(ctx: Context, why: String) {
    val intent = Intent(ctx, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Settings.canDrawOverlays(ctx)) {
        val ok = runCatching { ctx.startActivity(intent) }.isSuccess
        Player.log("$why → lecteur lancé directement" + if (ok) "" else " (échec)")
        if (ok) return
    }
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL, "Lecteur écran de verrouillage", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
    )
    val open = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val n = Notification.Builder(ctx, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("Vinyle")
        .setCategory(Notification.CATEGORY_ALARM)
        .setFullScreenIntent(open, true)
        .setAutoCancel(true)
        .build()
    val ok = runCatching { nm.notify(LOCK_NOTIF_ID, n) }.isSuccess
    Player.log("$why → notification plein écran" + if (ok) "" else " (refusée)")
}
