package com.guillaume.vinyl

import android.app.KeyguardManager
import android.app.WallpaperManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.SweepGradient
import android.os.Bundle
import android.view.Choreographer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Live wallpaper: sits behind the lock screen clock and the unlock gesture,
 * so the phone works normally while the record spins.
 */
class VinylWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = VinylEngine()

    inner class VinylEngine : Engine() {
        private val scope = MainScope()
        private val shader = RuntimeShader(MILKDROP)
        private val bg = Paint().apply { shader = this@VinylEngine.shader }
        private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clip = Path()

        private var visible = false
        private var scaled = false // false = full resolution (fallback)
        private var looping = false
        private var w = 1f
        private var h = 1f
        private var angle = 0f
        private var playAmount = 0f // 0 = paused look, 1 = playing look (smoothed)
        private fun nowMs() = System.nanoTime() / 1_000_000L // same clock as vsync frame times
        private var last = nowMs()
        private val start = last

        // Cover + grooves baked once per track at disc size; each frame only rotates this bitmap.
        private var discFor: Bitmap? = null
        private var disc: Bitmap? = null

        // Driven by the display's vsync so every frame lands evenly (no judder on 120 Hz screens).
        // 60 fps on the lock screen, 30 fps behind the home screen icons.
        private val choreographer = Choreographer.getInstance()
        private var lastDrawNs = 0L
        private var locked = true
        private var lockedCheckedNs = 0L
        private val frame = object : Choreographer.FrameCallback {
            override fun doFrame(ns: Long) {
                if (!visible) { looping = false; return }
                if (ns - lockedCheckedNs > 1_000_000_000L) { lockedCheckedNs = ns; locked = isLocked() }
                val gap = if (locked) 15_000_000L else 31_000_000L
                if (lastDrawNs == 0L || ns - lastDrawNs >= gap) { lastDrawNs = ns; draw(ns / 1_000_000L) }
                // Paused and settled: stop drawing entirely until the player state changes.
                looping = Player.state.value.isPlaying || playAmount > 0.01f
                if (looping) choreographer.postFrameCallback(this)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            setTouchEventsEnabled(false)
            // Render below screen resolution, the compositor upscales: far fewer shader pixels.
            // A wallpaper service has no display of its own (WindowManager metrics throw here),
            // so read the default display; if anything fails, just render at full resolution.
            runCatching {
                val m = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                getSystemService(android.hardware.display.DisplayManager::class.java)
                    .getDisplay(android.view.Display.DEFAULT_DISPLAY).getRealMetrics(m)
                surfaceHolder.setFixedSize((m.widthPixels * SCALE).toInt(), (m.heightPixels * SCALE).toInt())
                scaled = true
            }
            scope.launch { Player.state.collect { if (visible) kick() } }
        }

        override fun onDestroy() {
            scope.cancel()
            choreographer.removeFrameCallback(frame)
        }

        private fun kick() {
            if (!looping) last = nowMs() // no jump after a long pause
            choreographer.removeFrameCallback(frame)
            lastDrawNs = 0L
            choreographer.postFrameCallback(frame)
        }

        private fun isLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            choreographer.removeFrameCallback(frame)
            looping = false
            if (v) kick()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            w = width.toFloat(); h = height.toFloat()
            discFor = null
            shine.shader = SweepGradient(cx(), cy(), intArrayOf(0, 0x22FFFFFF, 0, 0, 0x22FFFFFF, 0), null)
            kick()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            looping = false
            choreographer.removeFrameCallback(frame)
        }

        // Home screen launchers send a tap command (screen pixels); the lock screen keeps touches for itself.
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP && hypot(x * k() - cx(), y * k() - cy()) < radius()) Player.togglePlayPause()
            return null
        }

        private fun k() = if (scaled) SCALE else 1f
        private fun cx() = w / 2
        private fun cy() = h * DISC_Y
        private fun radius() = min(w, h) * DISC_R

        private fun draw(now: Long) {
            val s = Player.state.value
            val colors = s.colors
            val dt = (now - last) / 1000f
            last = now
            playAmount += ((if (s.isPlaying) 1f else 0f) - playAmount) * min(1f, dt * 3f)
            angle = (angle + dt * 100f * playAmount) % 360f // ~17 rpm, eases to a stop on pause

            if (s.hasSession && (disc == null || discFor !== s.cover)) {
                discFor = s.cover
                disc = ShadeArt.vinyl(s.cover, (radius() * 2).toInt().coerceAtLeast(2), shine = false)
            }

            val holder = surfaceHolder
            val c = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                val t = ((now - start) / 1000f) % 3600f // keep float precision over long sessions
                val beat = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 2f) // 120 bpm feel
                shader.setFloatUniform("res", w, h)
                shader.setFloatUniform("t", t * (0.35f + 0.65f * playAmount))
                shader.setFloatUniform("pulse", beat.pow(3) * playAmount)
                shader.setFloatUniform("dim", if (s.hasSession) 0.45f + 0.55f * playAmount else 0.25f)
                shader.setColorUniform("c0", colors[0])
                shader.setColorUniform("c1", colors[1])
                shader.setColorUniform("c2", colors[2])
                if (s.hasSession) {
                    // don't shade the pixels the record covers anyway
                    c.save(); clip.rewind(); clip.addCircle(cx(), cy(), radius() * 0.99f, Path.Direction.CW)
                    c.clipOutPath(clip); c.drawPaint(bg); c.restore()
                    drawRecord(c)
                } else c.drawPaint(bg)
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }

        private fun drawRecord(c: Canvas) {
            val x = cx(); val y = cy(); val r = radius()
            fill.color = Color.argb(140, 0, 0, 0)
            c.drawCircle(x, y, r * 1.03f, fill) // soft rim
            disc?.let { c.save(); c.rotate(angle, x, y); c.drawBitmap(it, x - r, y - r, bmpPaint); c.restore() }
            c.drawCircle(x, y, r, shine) // light reflection stays still while the record turns
        }
    }
}

/** Disc position as fractions of the screen, shared with the lock screen controls. */
const val DISC_Y = 0.46f
const val DISC_R = 0.38f

/** Wallpaper surface resolution vs screen; the ribbons are soft so the upscale is invisible. */
private const val SCALE = 0.6f

/** MilkDrop-like silky light ribbons (AGSL). Prototyped in WebGL first. */
private const val MILKDROP = """
uniform float2 res;
uniform float t;
uniform float pulse;
uniform float dim;
layout(color) uniform half4 c0;
layout(color) uniform half4 c1;
layout(color) uniform half4 c2;

half4 main(float2 fc) {
    float2 uv = (fc - 0.5 * res) / min(res.x, res.y);
    float2 p = uv * 2.2;
    for (int i = 1; i < 6; i++) {
        float fi = float(i);
        p += float2(0.6 / fi * sin(fi * p.y + t * 0.35 + 0.3 * fi),
                    0.6 / fi * cos(fi * p.x + t * 0.27 - 0.5 * fi));
    }
    float3 col = float3(0.0);
    for (int k = 0; k < 5; k++) {
        float fk = float(k);
        float f = sin(p.x * (1.0 + 0.15 * fk) + p.y * (0.8 - 0.1 * fk) + fk * 0.9 + t * 0.2);
        float soft = exp(-abs(f) * 2.2);
        float sharp = exp(-abs(f) * 14.0);
        float3 c = k == 0 ? float3(c0.rgb) : (k == 2 ? float3(c2.rgb) : (k == 3 ? float3(c0.rgb) : float3(c1.rgb)));
        col += c * (soft * 0.28 + sharp * 0.55);
    }
    float vign = smoothstep(1.7, 0.15, length(uv));
    col *= (0.6 + 0.55 * pulse) * vign * dim;
    return half4(half3(col), 1.0);
}
"""
