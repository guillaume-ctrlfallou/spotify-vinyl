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
    // cached: reading controller.metadata unparcels the whole artwork each time
    private var meta: MediaMetadata? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) { meta = metadata; publish() }
        override fun onSessionDestroyed() = pick(emptyList())
    }

    override fun onListenerConnected() {
        val msm = getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(this, MediaListener::class.java)
        sessions = msm
        msm.addOnActiveSessionsChangedListener(sessionsListener, me)
        pick(msm.getActiveSessions(me))
        Player.log("service connecté")
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })
    }

    // Screen turned off with Spotify around: put our player on the lock screen for when it wakes.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_ON) {
                // shade progress only matters while someone can look at it
                handler.removeCallbacks(progressTick); progressTick.run(); return
            }
            handler.removeCallbacks(progressTick)
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
            meta = next?.metadata
        }
        Player.controller = current
        publish()
    }

    private fun publish() {
        val c = current ?: run { Player.state.value = PlayerState(); ShadePlayer.update(this); return }
        val m = meta
        val old = Player.state.value
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        // Spotify often changes the title before it sends the new art, so follow the bitmap itself, not the title.
        val cover = m?.cover() ?: m?.coverFromUri()
        val sameCover = cover != null && cover === old.cover
        Player.state.value = PlayerState(
            title = title,
            artist = artist,
            cover = cover,
            isPlaying = playing,
            hasSession = true,
            durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            colors = if (sameCover) old.colors else paletteColors(cover),
        )
        if (playing && !old.isPlaying && isLocked()) launchLockPlayer(this, "lecture sur téléphone verrouillé")
        ShadePlayer.update(this)
        handler.removeCallbacks(progressTick)
        if (playing && power.isInteractive) handler.postDelayed(progressTick, 1000)
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val power by lazy { getSystemService(PowerManager::class.java) }
    // Keeps the shade progress bar moving while music plays.
    private val progressTick = object : Runnable {
        override fun run() {
            ShadePlayer.update(this@MediaListener)
            if (Player.state.value.isPlaying && power.isInteractive) handler.postDelayed(this, 1000)
        }
    }

    private fun MediaMetadata.cover(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    // Some tracks only carry a link to the art: fetch it off the main thread, then publish again.
    private var uriArt: Pair<String, Bitmap>? = null
    private var uriLoading: String? = null
    private fun MediaMetadata.coverFromUri(): Bitmap? {
        val uri = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            .firstNotNullOfOrNull { getString(it)?.takeIf { u -> u.isNotBlank() } } ?: return null
        uriArt?.let { if (it.first == uri) return it.second }
        if (uriLoading == uri) return null
        uriLoading = uri
        Thread {
            val bmp = runCatching {
                // Only an encrypted download or another app's shared image; never plain http or local files.
                val u = android.net.Uri.parse(uri)
                val input = when (u.scheme) {
                    "https" -> java.net.URL(uri).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }.getInputStream()
                    "content" -> contentResolver.openInputStream(u)
                    else -> null
                }
                // cap at 4 MB so a bad link can't fill memory
                val bytes = input?.use { it.readNBytes(4 shl 20) } ?: return@runCatching null
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                o.inJustDecodeBounds = false
                while (o.outWidth / (o.inSampleSize.coerceAtLeast(1) * 2) >= 640) o.inSampleSize = o.inSampleSize.coerceAtLeast(1) * 2
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            }.getOrNull()
            handler.post {
                if (uriLoading == uri) uriLoading = null
                if (bmp != null) { uriArt = uri to bmp; publish() } else Player.log("pochette introuvable : $uri")
            }
        }.start()
        return null
    }
}
