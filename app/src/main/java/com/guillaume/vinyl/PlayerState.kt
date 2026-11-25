package com.guillaume.vinyl

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow

data class PlayerState(
    val title: String = "",
    val artist: String = "",
    val cover: Bitmap? = null,
    val isPlaying: Boolean = false,
    val hasSession: Boolean = false,
    val durationMs: Long = 0,
    /** Three accent colors taken from the cover. */
    val colors: IntArray = DEFAULT_COLORS,
)

val DEFAULT_COLORS = intArrayOf(0xFF6A3DE8.toInt(), 0xFF1DB954.toInt(), 0xFFE8417A.toInt())

fun paletteColors(cover: Bitmap?): IntArray {
    if (cover == null) return DEFAULT_COLORS
    val p = Palette.from(cover).maximumColorCount(16).generate()
    val found = listOfNotNull(p.vibrantSwatch, p.lightVibrantSwatch, p.dominantSwatch, p.darkVibrantSwatch, p.mutedSwatch)
        .map { it.rgb }.distinct()
    if (found.isEmpty()) return intArrayOf(Color.WHITE, Color.GRAY, Color.DKGRAY)
    return IntArray(3) { found[it % found.size] }
}

/** Shared between the listener service and the UI (same process). */
object Player {
    val state = MutableStateFlow(PlayerState())
    @Volatile var controller: MediaController? = null

    fun togglePlayPause() {
        val c = controller ?: return
        if (state.value.isPlaying) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}
