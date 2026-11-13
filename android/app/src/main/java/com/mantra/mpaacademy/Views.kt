package com.mantra.mpaacademy

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup

// The website's palette (app.css :root)
const val BG = 0xFF0B0D10.toInt()
const val BG2 = 0xFF141A21.toInt()
const val SLATE = 0xFF23303D.toInt()
const val LINE = 0xFF2A333D.toInt()
const val INK = 0xFFF2DDB4.toInt()
const val DIM = 0xFF8B8F94.toInt()
const val AMBER = 0xFFF59E0B.toInt()
const val AMBER2 = 0xFFFBBF24.toInt()
const val GREEN = 0xFF3FB950.toInt()

fun isAcc(c: String) = c.isNotEmpty() && c[0] != '-'

/** Group sizes written in an exercise's title ("7 + 3", "7 plus 3", "3+3+2"), or null when the title has none (request 18). */
fun titleGroups(name: String): List<Int>? {
    val m = Regex("(\\d+)(\\s*(?:\\+|plus)\\s*\\d+)+", RegexOption.IGNORE_CASE).find(name) ?: return null
    return Regex("\\d+").findAll(m.value.replace(Regex("plus", RegexOption.IGNORE_CASE), "+")).map { it.value.toInt() }.toList().takeIf { it.size > 1 && it.all { n -> n > 0 } }
}

/** Where a group of the row starts. The title's own numbers decide when it has them; otherwise the strokes do:
 *  a new group at every DUM (D), at the first stroke of a row, at a strong stroke (T, P) after a ghost note and after a DUM.
 *  K only fills a group up: it never restarts the numbers. */
fun groupStarts(row: List<String>, tg: List<Int>? = null): List<Int> {
    if (tg != null && row.size % tg.sum() == 0) return (0 until row.size / tg.sum()).flatMap { r -> var x = r * tg.sum(); tg.map { g -> x.also { x += g } } }
    return row.indices.filter { j ->
        val c = if (row[j].isEmpty()) ' ' else row[j][0].uppercaseChar()
        isAcc(row[j]) && (j == 0 || c == 'D' || (c != 'K' && (!isAcc(row[j - 1]) || row[j - 1][0].uppercaseChar() == 'D')))
    }
}

/** spoons() of lessons.js for a phrasing item: restart at every group start. */
fun spoons(row: List<String>, tg: List<Int>? = null, bar1: Int = 0): IntArray {
    // request 23: bar 1 is the traditional rhythm: its numbers restart on every accent (the site's spoons); bar 2 by groups
    val g = (groupStarts(row, tg).filter { it >= bar1 } + (0 until bar1).filter { isAcc(row[it]) }).toSet(); var n = 0
    return IntArray(row.size) { j -> n = if (g.contains(j) || j == 0 || j == bar1) 1 else n + 1; n }
}

class PhrInfo(val n: Int, val lab: String, val head: Boolean, val rev: Boolean)

/** phrInfo() of lessons.js: the groups of bar 2 ("7+1") and the "Reverse dynamics" section. */
fun phrInfo(rows: List<List<String>>, tg: List<Int>? = null): List<PhrInfo> {
    var sec = 0; var n = 0; var brk = false
    return rows.map { row ->
        if (brk) { sec = 1; n = 0; brk = false }
        val h = row.subList(row.size / 2, row.size); val st = groupStarts(h)
        val sz = st.mapIndexed { k, x -> (if (k + 1 < st.size) st[k + 1] else h.size) - x }
        val lead = if (h.isNotEmpty() && !isAcc(h[0])) listOf(if (st.isNotEmpty()) st[0] else h.size) else emptyList()
        val g = lead + sz; n++
        val o = PhrInfo(n, if (g.size > 1) g.joinToString("+") else (g.firstOrNull() ?: h.size).toString(), sec == 1 && n == 1, sec == 1)
        if (g.size == 1 && sec == 0) brk = true
        o
    }
}

/** One row of cells, drawn like the site: 1 px ink border, accent = inset 3 px bar, number above, spoon under, lit = amber. */
class CellsView(ctx: Context, private val row: List<String>, private val sp: IntArray, val cols: Int,
                private val fLetter: Typeface, private val fMono: Typeface) : View(ctx) {
    var lit = -1
        set(v) { if (v != field) { field = v; invalidate() } }
    private val d = resources.displayMetrics.density
    private val numH = 0f
    private val monoBold = Typeface.create(fMono, Typeface.BOLD)
    private val numB = 32 * d    // the numbers live below the cells, double size (20 instead of 10)
    private var cw = 0f; private var ch = 0f
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d; color = INK }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = fLetter }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = fMono; color = DIM; textSize = 20 * d }
    fun lineCount() = lines
    private fun pitch() = ch + numB + 6 * d
    private val lines get() = (row.size + cols - 1) / cols
    override fun onMeasure(w: Int, h: Int) {
        val W = MeasureSpec.getSize(w).toFloat()
        cw = minOf((W + (cols - 1) * d) / cols, 76 * d); ch = cw * 56f / 52f
        setMeasuredDimension(W.toInt(), (lines * pitch() - 6 * d).toInt())
    }
    override fun onDraw(c: Canvas) {
        for (j in row.indices) {
            val col = j % cols; val ln = j / cols
            val x = col * (cw - d); val top = ln * pitch() + numH
            val on = j == lit
            fill.color = if (on) AMBER else Color.TRANSPARENT
            c.drawRect(x, top, x + cw, top + ch, fill)
            box.color = if (on) AMBER else INK
            c.drawRect(x + d / 2, top + d / 2, x + cw - d / 2, top + ch - d / 2, box)
            if (isAcc(row[j])) { fill.color = INK; c.drawRect(x, top, x + 3 * d, top + ch, fill) }
            txt.color = if (on) BG else INK; txt.textSize = ch * 0.54f
            val bl = top + ch / 2 - (txt.ascent() + txt.descent()) / 2
            c.drawText(row[j], x + cw / 2, bl, txt)
            num.color = if (on) AMBER else DIM; num.typeface = if (sp[j] == 1) monoBold else fMono; num.textSize = (if (sp[j] == 1) 24 else 20) * d   // request 19/25: every 1 bold and 20 % larger, the rest normal; the number lights with its cell
            c.drawText("${sp[j]}", x + cw / 2, top + ch + 4 * d - num.ascent(), num)
        }
    }
    /** vertical centre of one line of cells, inside this view (the marker sits on the line being played) */
    fun lineCenter(l: Int) = l * pitch() + numH + ch / 2f
}

/** The round play button: 44 px, amber ring, the triangle of the site (36.67 27, 36.67 73, 76.67 50 in a 100 box). */
class PlayView(ctx: Context) : View(ctx) {
    var on = false
        set(v) { field = v; invalidate() }
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true }
    override fun onDraw(c: Canvas) {
        val lit = on || isPressed
        val r = width / 2f
        p.style = Paint.Style.FILL; p.color = if (lit) AMBER else Color.TRANSPARENT; c.drawCircle(r, r, r - d / 2, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = AMBER; c.drawCircle(r, r, r - d / 2, p)
        val s = width / 100f
        val path = Path().apply { moveTo(36.67f * s, 27f * s); lineTo(36.67f * s, 73f * s); lineTo(76.67f * s, 50f * s); close() }
        p.style = Paint.Style.FILL; p.color = if (lit) BG else AMBER; c.drawPath(path, p)
    }
    override fun setPressed(pr: Boolean) { super.setPressed(pr); invalidate() }
}

/** Wraps its children onto lines (the site's flex-wrap). */
class Flow(ctx: Context, private val gapX: Int, private val gapY: Int) : ViewGroup(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        val W = MeasureSpec.getSize(w); var x = 0; var y = 0; var lh = 0; var maxX = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i); if (v.visibility == GONE) continue
            v.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            if (x > 0 && x + v.measuredWidth > W) { x = 0; y += lh + gapY; lh = 0 }
            x += v.measuredWidth + gapX; lh = maxOf(lh, v.measuredHeight); maxX = maxOf(maxX, x - gapX)
        }
        setMeasuredDimension(if (MeasureSpec.getMode(w) == MeasureSpec.EXACTLY) W else maxX, y + lh)
    }
    override fun onLayout(ch: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val W = r - l
        // line heights first, so every child can be centred on its line
        val hs = ArrayList<Int>(); run { var x0 = 0; var h0 = 0
            for (i in 0 until childCount) { val v = getChildAt(i); if (v.visibility == GONE) continue
                if (x0 > 0 && x0 + v.measuredWidth > W) { hs.add(h0); x0 = 0; h0 = 0 }
                x0 += v.measuredWidth + gapX; h0 = maxOf(h0, v.measuredHeight) }
            hs.add(h0) }
        var x = 0; var y = 0; var line = 0; var lh = hs[0]
        for (i in 0 until childCount) {
            val v = getChildAt(i); if (v.visibility == GONE) continue
            if (x > 0 && x + v.measuredWidth > W) { x = 0; y += lh + gapY; line++; lh = hs[line] }
            v.layout(x, y + (lh - v.measuredHeight) / 2, x + v.measuredWidth, y + (lh - v.measuredHeight) / 2 + v.measuredHeight)
            x += v.measuredWidth + gapX
        }
    }
}

/** A background that lights while the control is pressed. */
fun pressBg(d: Float, fillOn: Int, stroke: Int, radius: Float, on: Boolean = false, fillOff: Int = Color.TRANSPARENT, dashed: Boolean = false): StateListDrawable {
    fun g(c: Int) = GradientDrawable().apply { setColor(c); if (stroke != 0) setStroke((d).toInt().coerceAtLeast(1), stroke); cornerRadius = radius * d }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), g(fillOn))
        addState(intArrayOf(), g(if (on) fillOn else fillOff))
    }
}

/** A cassette deck's skip button: a bar and two triangles, pointing back or forward (request 18). */
class SkipView(ctx: Context, private val next: Boolean, private val enabled: Boolean) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    init { isClickable = enabled; background = pressBg(d, 0x33F59E0B, 0, 6f) }
    override fun onDraw(c: Canvas) {
        p.color = if (enabled) AMBER2 else SLATE
        val w = width.toFloat(); val h = height.toFloat(); val s = minOf(w * 0.78f, 24 * d); val t = minOf(h * 0.42f, 9 * d)
        val l = (w - s) / 2f; val cy = h / 2f
        val tri = s * 0.36f; val bar = 2.5f * d
        fun tri(x0: Float) = Path().apply { if (next) { moveTo(x0, cy - t); lineTo(x0 + tri, cy); lineTo(x0, cy + t) } else { moveTo(x0 + tri, cy - t); lineTo(x0, cy); lineTo(x0 + tri, cy + t) }; close() }
        if (next) { c.drawPath(tri(l), p); c.drawPath(tri(l + tri), p); c.drawRect(l + 2 * tri + 1.5f * d, cy - t, l + 2 * tri + 1.5f * d + bar, cy + t, p) }
        else { c.drawRect(l, cy - t, l + bar, cy + t, p); c.drawPath(tri(l + bar + 1.5f * d), p); c.drawPath(tri(l + bar + 1.5f * d + tri), p) }
    }
}

/** The orientation switch: a plain rectangle shaped like the screen now (tall in portrait, wide in landscape). */
class OrientView(ctx: Context, private val landscape: Boolean) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = DIM }   // request 20: grey, not orange
    init { isClickable = true; background = pressBg(d, 0x33F59E0B, 0, 6f) }
    override fun onDraw(c: Canvas) {
        val w = (if (landscape) 26 else 16) * d; val h = (if (landscape) 16 else 26) * d
        val l = (width - w) / 2f; val t = (height - h) / 2f
        c.drawRoundRect(l, t, l + w, t + h, 3 * d, 3 * d, p)
    }
}
