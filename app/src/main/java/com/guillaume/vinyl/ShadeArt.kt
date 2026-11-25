package com.guillaume.vinyl

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import kotlin.math.min
import kotlin.random.Random

/** Same look as the wallpaper, as still images (notifications can't run shaders). */
object ShadeArt {

    /** Cover cut into a round record, with grooves, reflection and spindle hole. */
    fun vinyl(cover: Bitmap?, size: Int = 192, shine: Boolean = true): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val r = size / 2f
        val clip = Path().apply { addCircle(r, r, r, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        if (cover != null) {
            val side = min(cover.width, cover.height)
            val src = Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2)
            c.drawBitmap(cover, src, RectF(0f, 0f, size.toFloat(), size.toFloat()), p)
        } else {
            p.color = Color.rgb(17, 17, 17); c.drawCircle(r, r, r, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Color.argb(30, 0, 0, 0)
        var g = r * 0.97f
        while (g > r * 0.2f) { c.drawCircle(r, r, g, p); g -= r * 0.04f }
        c.restore()
        p.style = Paint.Style.FILL
        if (shine) {
            p.shader = SweepGradient(r, r, intArrayOf(0, 0x26FFFFFF, 0, 0, 0x26FFFFFF, 0), null)
            c.drawCircle(r, r, r, p); p.shader = null
        }
        p.color = Color.BLACK; c.drawCircle(r, r, r * 0.06f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = Color.argb(90, 255, 255, 255)
        c.drawCircle(r, r, r * 0.08f, p)
        return out
    }

    /** Dark background with soft glowing ribbons in the cover colors (MilkDrop-like, frozen). */
    fun ribbons(colors: IntArray, seed: Int, w: Int = 480, h: Int = 168): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565) // opaque: half the notification payload
        val c = Canvas(out)
        c.drawColor(Color.rgb(11, 11, 16))
        val rnd = Random(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        repeat(5) { i ->
            val col = colors[i % colors.size]
            val path = Path().apply {
                moveTo(-20f, h * rnd.nextFloat())
                cubicTo(w * 0.3f, h * (rnd.nextFloat() * 1.6f - 0.3f),
                    w * 0.65f, h * (rnd.nextFloat() * 1.6f - 0.3f),
                    w + 20f, h * rnd.nextFloat())
            }
            // wide soft glow, then a thin bright core
            p.maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
            p.strokeWidth = 46f; p.color = (col and 0x00FFFFFF) or 0x55000000
            c.drawPath(path, p)
            p.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
            p.strokeWidth = 2.5f; p.color = (col and 0x00FFFFFF) or 0xCC000000.toInt()
            c.drawPath(path, p)
        }
        // darken the left/text side slightly for legibility
        p.maskFilter = null; p.style = Paint.Style.FILL; p.color = Color.argb(70, 0, 0, 0)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return out
    }
}
