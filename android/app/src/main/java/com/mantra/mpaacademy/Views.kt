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

/** groupStarts() of lessons.js: a new group at every DUM, after a ghost note and after a DUM. */
fun groupStarts(row: List<String>): List<Int> = row.indices.filter { j ->
    isAcc(row[j]) && (row[j][0].uppercaseChar() == 'D' || j == 0 || !isAcc(row[j - 1]) || row[j - 1][0].uppercaseChar() == 'D')
}

/** spoons() of lessons.js for a phrasing item: restart at every group start. */
fun spoons(row: List<String>): IntArray {
    val g = groupStarts(row).toSet(); var n = 0
    return IntArray(row.size) { j -> n = if (g.contains(j) || j == 0) 1 else n + 1; n }
}

class PhrInfo(val n: Int, val lab: String, val head: Boolean)

/** phrInfo() of lessons.js: the groups of bar 2 ("7+1") and the "Reverse dynamics:" heading. */
fun phrInfo(rows: List<List<String>>): List<PhrInfo> {
    var sec = 0; var n = 0; var brk = false
    return rows.map { row ->
        if (brk) { sec = 1; n = 0; brk = false }
        val h = row.subList(row.size / 2, row.size); val st = groupStarts(h)
        val sz = st.mapIndexed { k, x -> (if (k + 1 < st.size) st[k + 1] else h.size) - x }
        val lead = if (h.isNotEmpty() && !isAcc(h[0])) listOf(if (st.isNotEmpty()) st[0] else h.size) else emptyList()
        val g = lead + sz; n++
        val o = PhrInfo(n, if (g.size > 1) g.joinToString("+") else (g.firstOrNull() ?: h.size).toString(), sec == 1 && n == 1)
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
    private val numH = 14 * d
    private var cw = 0f; private var ch = 0f
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d; color = INK }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = fLetter }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = fMono; color = DIM; textSize = 10 * d }
    fun lineCount() = lines
    private fun pitch() = ch + 2 * numH + 6 * d
    private val lines get() = (row.size + cols - 1) / cols
    override fun onMeasure(w: Int, h: Int) {
        val W = MeasureSpec.getSize(w).toFloat()
        cw = (W + (cols - 1) * d) / cols; ch = cw * 56f / 52f
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
            c.drawText("${j + 1}", x + cw / 2, top - 3 * d, num)
            c.drawText("${sp[j]}", x + cw / 2, top + ch + 3 * d + 10 * d, num)
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
