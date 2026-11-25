package com.guillaume.vinyl

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.media.session.PlaybackState
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Our player on the lock screen. The live wallpaper (vinyl + ribbons) shows through;
 * this window only adds the clock, the controls and the touch targets.
 * It never stays over the unlocked phone: it closes as soon as the phone is unlocked.
 */
class LockActivity : ComponentActivity() {

    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        // Cover the camera cutout too, so our layout matches the full-screen wallpaper.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        registerReceiver(unlocked, IntentFilter(Intent.ACTION_USER_PRESENT))
        Player.log("lecteur affiché")
        setContent { LockScreen(onUnlock = ::unlock) }
    }

    override fun onResume() {
        super.onResume()
        getSystemService(NotificationManager::class.java).cancel(LOCK_NOTIF_ID)
        // Opened while the phone is already unlocked (or nothing to show): don't sit on top of anything.
        val unlockedNow = !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            getSystemService(PowerManager::class.java).isInteractive
        if (unlockedNow || !Player.state.value.hasSession) {
            Player.log(if (unlockedNow) "lecteur fermé : téléphone pas verrouillé" else "lecteur fermé : pas de musique")
            finish()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        super.onDestroy()
    }

    private fun unlock() {
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
            })
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val s by Player.state.collectAsState()
    val accent = Color(s.colors[0])
    var dragged = 0f

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -150.dp.toPx()) onUnlock() },
                ) { _, dy -> dragged += dy }
            }
    ) {
        // Same geometry as the wallpaper's disc.
        val r: Dp = min(maxWidth, maxHeight) * DISC_R
        val cy: Dp = maxHeight * DISC_Y

        Clock(Modifier.align(Alignment.TopCenter).padding(top = 56.dp))

        Box(
            Modifier
                .offset(x = maxWidth / 2 - r, y = cy - r)
                .size(r * 2)
                .clip(CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null) { Player.togglePlayPause() }
        )

        Column(
            Modifier.fillMaxWidth().offset(y = cy + r + 28.dp).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                s.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text(
                s.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Progress(s.durationMs, accent)
            Spacer(Modifier.height(14.dp))
            // Arrows at the edges, centre left free for the fingerprint sensor; the disc does play/pause.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Control(android.R.drawable.ic_media_previous, 52.dp, accent.copy(alpha = 0.22f)) { Player.previous() }
                Control(android.R.drawable.ic_media_next, 52.dp, accent.copy(alpha = 0.22f)) { Player.next() }
            }
        }

        Text(
            "Glisse vers le haut pour déverrouiller",
            color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
        )
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light)
        Text(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
    }
}

@Composable
private fun Progress(durationMs: Long, accent: Color) {
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            Player.controller?.playbackState?.let { ps ->
                // Position is reported at lastPositionUpdateTime; extrapolate to now.
                pos = ps.position + if (ps.state == PlaybackState.STATE_PLAYING)
                    ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong() else 0L
            }
            delay(500)
        }
    }
    val p = if (durationMs > 0) (pos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxWidth(p).height(4.dp).background(accent))
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(time(pos), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(time(durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

private fun time(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Control(icon: Int, size: Dp, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), null, Modifier.size(size * 0.55f), colorFilter = ColorFilter.tint(Color.White))
    }
}
