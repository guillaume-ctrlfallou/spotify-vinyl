package com.guillaume.vinyl

import android.app.KeyguardManager
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
        Player.log("service connecté")
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    // Screen turned off with Spotify around: put our player on the lock screen for when it wakes.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Player.state.value.hasSession) launchLockPlayer(c, "écran éteint")
            else Player.log("écran éteint, pas de session Spotify")
        }
    }

    private fun isLocked(): Boolean =
        getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive


    override fun onListenerDisconnected() {
        sessions?.removeOnActiveSessionsChangedListener(sessionsListener)
        runCatching { unregisterReceiver(screenOff) }
        current?.unregisterCallback(callback)
        current = null
        Player.controller = null
        handler.removeCallbacks(progressTick)
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
        val c = current ?: run { Player.state.value = PlayerState(); ShadePlayer.update(this); return }
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
        if (playing && !old.isPlaying && isLocked()) launchLockPlayer(this, "lecture sur téléphone verrouillé")
        ShadePlayer.update(this)
        handler.removeCallbacks(progressTick)
        if (playing) handler.postDelayed(progressTick, 4000)
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    // Keeps the shade progress bar moving while music plays.
    private val progressTick = object : Runnable {
        override fun run() {
            ShadePlayer.update(this@MediaListener)
            if (Player.state.value.isPlaying) handler.postDelayed(this, 4000)
        }
    }

    private fun MediaMetadata.cover(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
}
