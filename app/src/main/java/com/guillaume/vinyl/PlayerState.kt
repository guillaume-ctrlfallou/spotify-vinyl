package com.guillaume.vinyl

import android.graphics.Bitmap
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow

data class PlayerState(
    val title: String = "",
    val artist: String = "",
    val cover: Bitmap? = null,
    val isPlaying: Boolean = false,
    val hasSession: Boolean = false,
)

/** Shared between the listener service and the UI (same process). */
object Player {
    val state = MutableStateFlow(PlayerState())
    @Volatile var controller: MediaController? = null

    fun togglePlayPause() {
        val c = controller ?: return
        if (state.value.isPlaying) c.transportControls.pause() else c.transportControls.play()
    }
}
