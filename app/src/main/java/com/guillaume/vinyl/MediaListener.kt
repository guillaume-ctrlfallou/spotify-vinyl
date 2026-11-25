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
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.PowerManager
import android.service.notification.NotificationListenerService

private const val SPOTIFY = "com.spotify.music"
private const val CHANNEL = "player"
const val PLAYER_NOTIF_ID = 1

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

    // Screen turned off while music plays: put the player behind the lock screen.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Player.state.value.isPlaying) showPlayer()
        }
    }

    override fun onListenerConnected() {
        val msm = getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(this, MediaListener::class.java)
        sessions = msm
        msm.addOnActiveSessionsChangedListener(sessionsListener, me)
        pick(msm.getActiveSessions(me))
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
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
        val c = current
        if (c == null) {
            Player.state.value = PlayerState()
            return
        }
        val m = c.metadata
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val wasPlaying = Player.state.value.isPlaying
        Player.state.value = PlayerState(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            cover = m?.cover(),
            isPlaying = playing,
            hasSession = true,
        )
        if (playing && !wasPlaying && isLocked()) showPlayer()
    }

    private fun MediaMetadata.cover(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    private fun isLocked(): Boolean =
        getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive

    /** A full-screen notification is the allowed way to open an activity over the lock screen. */
    private fun showPlayer() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lecteur", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, PlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val s = Player.state.value
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(s.title.ifEmpty { "Musique en cours" })
            .setContentText(s.artist)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(PLAYER_NOTIF_ID, n) }
    }
}
