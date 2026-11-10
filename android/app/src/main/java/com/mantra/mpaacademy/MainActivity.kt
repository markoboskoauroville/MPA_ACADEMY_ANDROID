package com.mantra.mpaacademy

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import org.json.JSONObject

private const val BG = 0xFF0B0D10.toInt()
private const val BG2 = 0xFF141A21.toInt()
private const val INK = 0xFFF2DDB4.toInt()
private const val DIM = 0xFF8B8F94.toInt()
private const val AMBER = 0xFFF59E0B.toInt()
private const val LINE = 0xFF2A333D.toInt()

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private val cats = ArrayList<Pair<String, List<Exercise>>>()
    private var player: Player? = null
    private var screen = 0   // 0 home, 1 player
    private var ticker: Runnable? = null
    private var bars = intArrayOf(0, 0, 0, 0)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** A control that lights while it is pressed (pressable-controls rule of 10.11.2026). */
    private fun press(base: Int, on: Boolean = false, stroke: Int = LINE): StateListDrawable {
        fun d(c: Int) = GradientDrawable().apply { setColor(c); setStroke(dp(1), stroke); cornerRadius = dp(6).toFloat() }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), d(AMBER))
            addState(intArrayOf(), d(if (on) AMBER else base))
        }
    }
    private fun txt(s: String, sp: Float, c: Int = INK, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = sp; setTextColor(c); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun btn(s: String, on: Boolean = false, f: () -> Unit) = TextView(this).apply {
        text = s; textSize = 18f; gravity = Gravity.CENTER; minHeight = dp(48); minimumWidth = dp(48)
        setPadding(dp(12), dp(6), dp(12), dp(6)); isClickable = true
        setTextColor(if (on) BG else INK); background = press(BG2, on)
        setOnTouchListener { v, e -> (v as TextView).setTextColor(if (e.action == android.view.MotionEvent.ACTION_DOWN || on) BG else INK); false }
        setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val j = JSONObject(assets.open("exercises.json").bufferedReader().readText())
        val ca = j.getJSONArray("categories")
        for (i in 0 until ca.length()) {
            val c = ca.getJSONObject(i); val ea = c.getJSONArray("exercises"); val l = ArrayList<Exercise>()
            for (k in 0 until ea.length()) {
                val e = ea.getJSONObject(k); val ra = e.getJSONArray("rows")
                l.add(Exercise(e.getString("code"), e.getString("name"), e.getInt("base"), e.getInt("loops"), e.getString("notes"),
                    (0 until ra.length()).map { r -> val x = ra.getJSONArray(r); (0 until x.length()).map { x.getString(it) } }))
            }
            cats.add(c.getString("name") to l)
        }
        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, w ->
            val i = w.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(i.left + dp(16), i.top + dp(16), i.right + dp(16), i.bottom + dp(16)); w
        }
        home()
        if (intent.getBooleanExtra("pin", false)) pinIcon()
    }

    /** Asks the launcher to put this app's icon on the home screen (adb: am start --ez pin true). */
    private fun pinIcon() {
        val sm = getSystemService(android.content.pm.ShortcutManager::class.java)
        if (!sm.isRequestPinShortcutSupported) return
        val i = android.content.Intent(this, MainActivity::class.java).setAction(android.content.Intent.ACTION_MAIN)
        val info = android.content.pm.ShortcutInfo.Builder(this, "mpa-home")
            .setShortLabel("MPA Academy").setIcon(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_play)).setIntent(i).build()
        sm.requestPinShortcut(info, null)
    }

    private fun home() {
        stopPlay(); screen = 0; root.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(txt("MPA ACADEMY", 26f, INK, true))
        col.addView(txt("Mantreshwar Percussions Academy", 14f, DIM))
        for ((name, list) in cats) {
            col.addView(txt(name.uppercase(), 15f, AMBER, true).apply { setPadding(0, dp(24), 0, dp(8)) })
            for (e in list) col.addView(btn("${e.code}   ${e.name}") { player(e) }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            })
        }
        root.addView(ScrollView(this).apply { addView(col) })
    }

    private fun player(e: Exercise) {
        stopPlay(); screen = 1; root.removeAllViews()
        var speed = 4; var voice = false; var notesOn = false
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        top.addView(btn("◀ BACK") { home() }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2) })
        top.addView(txt("${e.code}  ${e.name}", 20f, INK, true).apply { setPadding(0, dp(10), 0, dp(6)) })
        val soundRow = LinearLayout(this); val speedRow = LinearLayout(this)
        val play = btn("▶  PLAY") {}
        val status = txt("", 14f, DIM)
        val cellViews = ArrayList<ArrayList<TextView>>()
        val rowBoxes = ArrayList<View>(); val reps = ArrayList<TextView>()
        lateinit var scroll: ScrollView
        fun restart() { if (player != null) { stopPlay(); startPlay(e, speed, voice, cellViews, rowBoxes, reps, scroll, play) } }
        fun fill() {
            soundRow.removeAllViews(); speedRow.removeAllViews()
            soundRow.addView(btn("DRUM", !voice) { voice = false; fill(); restart() }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            soundRow.addView(btn("VOICE", voice) { voice = true; fill(); restart() }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            for (v in listOf(1, 2, 4, 8, 16, 32)) speedRow.addView(btn("16/$v", v == speed) { speed = v; fill(); restart() }.apply {
                textSize = 14f; setPadding(0, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
        }
        fill()
        top.addView(soundRow); top.addView(txt("SPEED", 12f, DIM).apply { setPadding(0, dp(8), 0, dp(4)) }); top.addView(speedRow)
        play.setOnClickListener {
            if (player != null) { stopPlay(); play.text = "▶  PLAY"; lightsOff(cellViews, rowBoxes, reps) }
            else startPlay(e, speed, voice, cellViews, rowBoxes, reps, scroll, play)
        }
        top.addView(play.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) } })
        top.addView(status)
        col.addView(top)
        // the rows: two bars of 8 cells, numbers above, spoons under
        for ((ri, row) in e.rows.withIndex()) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(8)) }
            val head = LinearLayout(this)
            head.addView(txt("${ri + 1}", 13f, AMBER, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            val rep = txt("", 13f, AMBER, true); reps.add(rep); head.addView(rep)
            box.addView(head)
            val cl = ArrayList<TextView>()
            for (half in 0..1) {
                val line = LinearLayout(this)
                var spoon = 0
                // spoons restart at every accent over the whole row
                val sp = IntArray(row.size); var s = 0
                for (i in row.indices) { if (row[i] != "-" && row[i] != "") s = 1 else s++; sp[i] = s }
                for (i in half * 8 until half * 8 + 8) {
                    val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
                    cell.addView(txt("${i + 1}", 10f, DIM).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(-1, -2) })
                    val accent = row[i] != "-" && row[i] != ""
                    val t = TextView(this).apply {
                        text = if (row[i] == "-") "–" else row[i]; textSize = 22f; gravity = Gravity.CENTER; setTextColor(INK)
                        layoutParams = LinearLayout.LayoutParams(-1, dp(46))
                        setBackgroundColor(BG)
                    }
                    val wrap = FrameLayout(this).apply {
                        background = GradientDrawable().apply { setStroke(dp(1), INK) }
                        addView(t)
                        if (accent) addView(View(this@MainActivity).apply { setBackgroundColor(INK); layoutParams = FrameLayout.LayoutParams(dp(4), -1) })
                    }
                    cell.addView(wrap)
                    cell.addView(txt("${sp[i]}", 10f, DIM).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(-1, -2) })
                    spoon++
                    line.addView(cell); cl.add(t)
                }
                box.addView(line)
            }
            cellViews.add(cl); rowBoxes.add(box)
            col.addView(box)
            col.addView(View(this).apply { setBackgroundColor(LINE); layoutParams = LinearLayout.LayoutParams(-1, dp(1)) })
        }
        val notesBtn = btn("NOTES") {}
        val notesTxt = txt(e.notes, 16f, INK).apply { visibility = View.GONE; setPadding(0, dp(10), 0, 0) }
        notesBtn.setOnClickListener { notesOn = !notesOn; notesTxt.visibility = if (notesOn) View.VISIBLE else View.GONE }
        col.addView(notesBtn.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) } })
        col.addView(notesTxt)
        scroll = ScrollView(this).apply { addView(col) }
        root.addView(scroll)
        (status as TextView).text = "Bar 1 is Malfuf. Bar 2 is the phrasing."
    }

    private fun lightsOff(cv: List<List<TextView>>, rb: List<View>, reps: List<TextView>) {
        for (r in cv) for (t in r) { t.setBackgroundColor(BG); t.setTextColor(INK) }
        rb.forEach { it.setBackgroundColor(Color.TRANSPARENT) }; reps.forEach { it.text = "" }
    }

    private fun startPlay(e: Exercise, speed: Int, voice: Boolean, cv: List<List<TextView>>, rb: List<View>, reps: List<TextView>, sc: ScrollView, play: TextView) {
        val p = Player(e, Bank(this, if (voice) "voice" else "drum"), speed) {}
        player = p; play.text = "■  STOP"; p.start()
        var last = -1; var lr = -1; var lj = -1
        val tick = object : Runnable {
            override fun run() {
                if (player !== p) return
                p.heard()?.let { (r, j, rep) ->
                    if (r != lr || j != lj) {
                        if (lr >= 0) { cv[lr][lj].setBackgroundColor(BG); cv[lr][lj].setTextColor(INK) }
                        cv[r][j].setBackgroundColor(AMBER); cv[r][j].setTextColor(BG); lr = r; lj = j
                    }
                    if (r != last) {
                        if (last >= 0) { rb[last].setBackgroundColor(Color.TRANSPARENT); reps[last].text = "" }
                        rb[r].setBackgroundColor(BG2); last = r
                        sc.smoothScrollTo(0, maxOf(0, rb[r].top - dp(220)))
                    }
                    reps[r].text = "$rep / ${e.loops}"
                }
                root.postDelayed(this, 16)
            }
        }
        ticker = tick; root.post(tick)
    }

    private fun stopPlay() { player?.stop(); player = null; ticker?.let { root.removeCallbacks(it) }; ticker = null }

    @Deprecated("back")
    override fun onBackPressed() { if (screen == 1) home() else super.onBackPressed() }
    override fun onPause() { super.onPause(); if (screen == 1) { stopPlay(); home().also { } } }
}
