package com.guillaume.vinyl

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

private const val SPOTIFY = "com.spotify.music"
private const val CHANNEL = "lock"
const val LOCK_NOTIF_ID = 1

/**
 * Notification access is what lets us read Spotify's media session.
 * We never touch notifications themselves.
 */
class MediaListener : NotificationListenerService() {

    private var sessions: MediaSessionManager? = null
    private var current: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick(emptyList())
    }

    override fun onListenerConnected() {
        val msm = getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(this, MediaListener::class.java)
        sessions = msm
        msm.addOnActiveSessionsChangedListener(sessionsListener, me)
        pick(msm.getActiveSessions(me))
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    // Screen turned off with Spotify around: put our player on the lock screen for when it wakes.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Player.state.value.hasSession) showLockPlayer()
        }
    }

    private fun isLocked(): Boolean =
        getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive

    /** A full-screen notification is the allowed way to open an activity on the lock screen. */
    private fun showLockPlayer() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lecteur écran de verrouillage", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Vinyle")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(LOCK_NOTIF_ID, n) }
    }

    override fun onListenerDisconnected() {
        sessions?.removeOnActiveSessionsChangedListener(sessionsListener)
        runCatching { unregisterReceiver(screenOff) }
        current?.unregisterCallback(callback)
        current = null
        Player.controller = null
    }

    private fun pick(list: List<MediaController>) {
        val next = list.firstOrNull { it.packageName == SPOTIFY }
        if (next?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = next
            next?.registerCallback(callback)
        }
        Player.controller = current
        publish()
    }

    private fun publish() {
        val c = current ?: run { Player.state.value = PlayerState(); return }
        val m = c.metadata
        val cover = m?.cover()
        val old = Player.state.value
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        Player.state.value = PlayerState(
            title = title,
            artist = artist,
            cover = cover,
            isPlaying = playing,
            hasSession = true,
            durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            // metadata hands out a new Bitmap each call, so compare the track instead
            colors = if (title == old.title && artist == old.artist && old.cover != null) old.colors else paletteColors(cover),
        )
        if (playing && !old.isPlaying && isLocked()) showLockPlayer()
    }

    private fun MediaMetadata.cover(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
}
