package com.guillaume.vinyl

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val fallback = listOf(Color(0xFF6A3DE8), Color(0xFF1DB954), Color(0xFFE8417A))

@Composable
fun VinylScreen(state: PlayerState, onToggle: () -> Unit) {
    val colors by produceState(fallback, state.cover) { value = coverColors(state.cover) }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Mist(colors, state.isPlaying)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Vinyl(state.cover, state.isPlaying, onToggle)
            Spacer(Modifier.height(40.dp))
            Text(
                state.title.ifEmpty { if (state.hasSession) "" else "Lance une musique sur Spotify" },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text(
                state.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
            )
        }
    }
}

private suspend fun coverColors(cover: Bitmap?): List<Color> {
    if (cover == null) return fallback
    return withContext(Dispatchers.Default) {
        val p = Palette.from(cover).generate()
        listOfNotNull(p.vibrantSwatch, p.dominantSwatch, p.darkVibrantSwatch, p.lightVibrantSwatch, p.mutedSwatch)
            .map { Color(it.rgb) }
            .distinct()
            .take(3)
            .ifEmpty { fallback }
            .let { if (it.size < 3) (it + it + it).take(3) else it }
    }
}

/** Fog: big soft color clouds drifting slowly, their light beating like a pulse. */
@Composable
private fun Mist(colors: List<Color>, playing: Boolean) {
    val c0 by animateColorAsState(colors[0], tween(1500), label = "c0")
    val c1 by animateColorAsState(colors[1], tween(1500), label = "c1")
    val c2 by animateColorAsState(colors[2], tween(1500), label = "c2")
    val t = rememberInfiniteTransition(label = "mist")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "drift")
    val beat by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat"
    )
    val strength by animateFloatAsState(if (playing) 1f else 0.25f, tween(1200), label = "strength")

    Canvas(Modifier.fillMaxSize().blur(48.dp)) {
        val r = max(size.width, size.height) * 0.55f
        listOf(c0, c1, c2).forEachIndexed { i, c ->
            val a = (drift + i / 3f) * 2f * PI.toFloat()
            val center = Offset(
                size.width * (0.5f + 0.32f * sin(a)),
                size.height * (0.5f + 0.28f * cos(a * 0.8f + i)),
            )
            // each cloud beats slightly out of phase with the others
            val pulse = 0.5f + 0.5f * sin((beat + i * 0.33f) * PI.toFloat())
            val alpha = (0.30f + 0.40f * pulse * strength).coerceIn(0f, 1f)
            drawCircle(
                Brush.radialGradient(
                    listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.35f), Color.Transparent),
                    center = center, radius = r * (0.9f + 0.15f * pulse * strength),
                ),
                radius = r * 1.1f, center = center,
            )
        }
    }
}

@Composable
private fun Vinyl(cover: Bitmap?, playing: Boolean, onToggle: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        // 33 rpm feel; stops exactly where it is when paused and resumes from there
        while (playing) {
            rotation.animateTo(rotation.value + 360f, tween(3600, easing = LinearEasing))
        }
    }
    val image = remember(cover) { cover?.asImageBitmap() }

    Box(
        Modifier
            .size(300.dp)
            .clip(CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation.value }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val R = size.minDimension / 2
                drawCircle(Color(0xFF111111), R)
                // grooves
                var g = R * 0.98f
                while (g > R * 0.40f) {
                    drawCircle(Color.White.copy(alpha = 0.045f), g, style = Stroke(1f))
                    g -= R * 0.018f
                }
                drawCircle(Color.Black, R * 0.39f)
            }
            if (image != null) {
                Image(
                    image, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(110.dp).clip(CircleShape),
                )
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF050505)))
        }
        // Static light reflection: stays still while the record turns underneath.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent,
                        Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent,
                    )
                )
            )
        }
    }
}
