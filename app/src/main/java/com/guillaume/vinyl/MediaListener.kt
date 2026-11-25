package com.guillaume.vinyl

import android.content.ComponentName
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
    }

    override fun onListenerDisconnected() {
        sessions?.removeOnActiveSessionsChangedListener(sessionsListener)
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
        Player.state.value = PlayerState(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            cover = m?.cover(),
            isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            hasSession = true,
        )
    }

    private fun MediaMetadata.cover(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
}
