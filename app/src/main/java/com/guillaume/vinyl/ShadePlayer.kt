package com.guillaume.vinyl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.session.PlaybackState
import android.os.SystemClock
import android.widget.RemoteViews

private const val CHANNEL = "shade"
private const val NOTIF_ID = 2

/**
 * Our player in the notification shade. Hidden on the lock screen (VISIBILITY_SECRET),
 * where the integrated overlay already does the job.
 */
object ShadePlayer {
    private var scaledFor: Bitmap? = null
    private var scaled: Bitmap? = null

    fun update(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val s = Player.state.value
        if (!s.hasSession) { nm.cancel(NOTIF_ID); return }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lecteur (volet)", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
        // Small copy of the cover: notifications have a size limit.
        if (s.cover !== scaledFor) {
            scaledFor = s.cover
            scaled = s.cover?.let { Bitmap.createScaledBitmap(it, 256, 256 * it.height / it.width.coerceAtLeast(1), true) }
        }
        val pos = Player.controller?.playbackState?.let { ps ->
            ps.position + if (ps.state == PlaybackState.STATE_PLAYING)
                ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong() else 0L
        } ?: 0L
        // Dark tint of the cover's main color as background.
        val c = s.colors[0]
        val bg = Color.rgb(Color.red(c) * 35 / 100, Color.green(c) * 35 / 100, Color.blue(c) * 35 / 100)

        fun views(layout: Int) = RemoteViews(ctx.packageName, layout).apply {
            setInt(R.id.root, "setBackgroundColor", bg)
            if (scaled != null) setImageViewBitmap(R.id.cover, scaled) else setImageViewResource(R.id.cover, android.R.drawable.ic_media_play)
            setTextViewText(R.id.title, s.title)
            setTextViewText(R.id.artist, s.artist)
            setImageViewResource(R.id.play, if (s.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            setOnClickPendingIntent(R.id.play, action(ctx, "toggle"))
            setOnClickPendingIntent(R.id.next, action(ctx, "next"))
            if (layout == R.layout.notif_big) {
                setOnClickPendingIntent(R.id.prev, action(ctx, "prev"))
                setProgressBar(R.id.progress, 1000, if (s.durationMs > 0) (pos * 1000 / s.durationMs).toInt() else 0, false)
                setTextViewText(R.id.pos, time(pos))
                setTextViewText(R.id.dur, time(s.durationMs))
            }
        }

        val open = ctx.packageManager.getLaunchIntentForPackage("com.spotify.music")?.let {
            PendingIntent.getActivity(ctx, 10, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(views(R.layout.notif_small))
            .setCustomBigContentView(views(R.layout.notif_big))
            .setOngoing(s.isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }

    private fun action(ctx: Context, what: String) = PendingIntent.getBroadcast(
        ctx, what.hashCode(), Intent(ctx, ControlReceiver::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE
    )

    private fun time(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}

class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            "toggle" -> Player.togglePlayPause()
            "next" -> Player.next()
            "prev" -> Player.previous()
        }
    }
}
