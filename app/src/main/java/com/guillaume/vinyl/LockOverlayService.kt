package com.guillaume.vinyl

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.media.session.PlaybackState
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.min

/**
 * Lock screen controls as small floating windows ON TOP of Samsung's own lock screen,
 * instead of a full-screen window that replaces it. The lock screen stays active underneath:
 * fingerprint, swipe to unlock, clock and notifications all keep working.
 * Accessibility overlays are the only windows Android lets an app put above the lock screen.
 */
class LockOverlayService : AccessibilityService() {

    companion object {
        @Volatile var running = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private val views = mutableListOf<View>()
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var bar: ProgressBarView
    private lateinit var prev: ImageView
    private lateinit var next: ImageView

    private val events = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_ON || i.action == Intent.ACTION_SCREEN_OFF) stepAside = false
            refresh()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            if (views.isNotEmpty()) handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        running = true
        // Only window changes of the system UI (shade, PIN pad), never other apps and never screen content.
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = arrayOf("com.android.systemui")
        }
        wm = getSystemService(WindowManager::class.java)
        registerReceiver(events, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        Player.log("mode intégré actif")
        refresh()
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(events) }
        hide()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = SystemClock.uptimeMillis()
        if (event == null) return
        if (views.isNotEmpty() || stepAside) Player.log("interface système : ${event.className?.toString()?.substringAfterLast('.')}")
        if (now - lastSwitch < 700) return
        if (views.isNotEmpty() && now - lastOutside < 1500) {
            // touched the lock screen and the system UI changed right after: shade or PIN pad opened
            stepAside = true; lastSwitch = now; hide()
        } else if (stepAside && views.isEmpty()) {
            // system UI changed back (shade or PIN pad closed)
            stepAside = false; lastSwitch = now; refresh()
        }
    }
    override fun onInterrupt() {}

    // Hidden while the shade or the PIN pad is open: a touch outside our windows followed by a system UI change.
    private var stepAside = false
    private var lastOwnDown = 0L
    private var lastOutside = 0L
    private var lastSwitch = 0L
    private val stepAsideCheck = Runnable {
        if (SystemClock.uptimeMillis() - lastOwnDown > 200) lastOutside = SystemClock.uptimeMillis()
    }

    private fun shouldShow(): Boolean =
        !stepAside && Player.state.value.hasSession &&
            getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            getSystemService(PowerManager::class.java).isInteractive

    private fun refresh() {
        if (!shouldShow()) { hide(); return }
        if (views.isEmpty()) { show(); handler.post(tick) }
        val s = Player.state.value
        if (title.text != s.title) title.text = s.title
        if (artist.text != s.artist) artist.text = s.artist
        bar.accent = s.colors[0]
        bar.duration = s.durationMs
        bar.position = Player.controller?.playbackState?.let { ps ->
            ps.position + if (ps.state == PlaybackState.STATE_PLAYING)
                ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong() else 0L
        } ?: 0L
        bar.invalidate()
        val bg = (s.colors[0] and 0x00FFFFFF) or 0x40000000
        (prev.background as GradientDrawable).setColor(bg)
        (next.background as GradientDrawable).setColor(bg)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun show() {
        val b = wm.currentWindowMetrics.bounds
        val w = b.width(); val h = b.height()
        val r = (min(w, h) * DISC_R).toInt()
        val cy = (h * DISC_Y).toInt()

        // Invisible touch target over the wallpaper's disc.
        add(View(this).apply {
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_UP &&
                    hypot(e.x - v.width / 2f, e.y - v.height / 2f) < v.width / 2f) Player.togglePlayPause()
                true
            }
        }, w / 2 - r, cy - r, 2 * r, 2 * r, touchable = true)

        // Title, artist, progress: display only, touches pass through to the lock screen.
        val infoTop = cy + r + dp(20)
        title = text(20f, true); artist = text(14f, false); bar = ProgressBarView(this)
        add(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(title); addView(artist)
            addView(bar, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(10) })
        }, dp(32), infoTop, w - dp(64), dp(110), touchable = false)

        // Arrows at the edges, centre left free for the fingerprint sensor.
        val arrowY = infoTop + dp(116)
        prev = arrow(android.R.drawable.ic_media_previous) { Player.previous() }
        next = arrow(android.R.drawable.ic_media_next) { Player.next() }
        add(prev, dp(28), arrowY, dp(52), dp(52), touchable = true)
        add(next, w - dp(28) - dp(52), arrowY, dp(52), dp(52), touchable = true)
    }

    private fun hide() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(stepAsideCheck)
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
    }

    private fun add(v: View, x: Int, y: Int, width: Int, height: Int, touchable: Boolean) {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val lp = WindowManager.LayoutParams(width, height, x, y,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val root = if (!touchable) v else object : android.widget.FrameLayout(this) {
            override fun dispatchTouchEvent(e: MotionEvent): Boolean {
                // A tap on one of our other windows also reaches here as "outside": wait a moment to tell them apart.
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { handler.postDelayed(stepAsideCheck, 60); return true }
                if (e.actionMasked == MotionEvent.ACTION_DOWN) { lastOwnDown = SystemClock.uptimeMillis(); handler.removeCallbacks(stepAsideCheck) }
                return super.dispatchTouchEvent(e)
            }
        }.apply { addView(v, -1, -1) }
        runCatching { wm.addView(root, lp); views += root }.onFailure { Player.log("fenêtre refusée : ${it.message}") }
    }

    private fun text(size: Float, bold: Boolean) = TextView(this).apply {
        textSize = size
        setTextColor(if (bold) Color.WHITE else Color.argb(180, 255, 255, 255))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    private fun arrow(icon: Int, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(icon)
        setColorFilter(Color.WHITE)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x40FFFFFF) }
        setOnClickListener { onClick() }
    }
}

/** Thin progress bar with elapsed / total time underneath. */
class ProgressBarView(ctx: Context) : View(ctx) {
    var accent = Color.WHITE
    var duration = 0L
    var position = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dens = ctx.resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val h = 4 * dens
        p.color = Color.argb(60, 255, 255, 255)
        c.drawRoundRect(0f, 0f, width.toFloat(), h, h, h, p)
        val f = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        p.color = accent
        c.drawRoundRect(0f, 0f, width * f, h, h, h, p)
        p.color = Color.argb(160, 255, 255, 255)
        p.textSize = 12 * dens
        p.setShadowLayer(6f, 0f, 0f, Color.BLACK)
        c.drawText(time(position), 0f, h + 18 * dens, p)
        val t = time(duration)
        c.drawText(t, width - p.measureText(t), h + 18 * dens, p)
        p.clearShadowLayer()
    }

    private fun time(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
