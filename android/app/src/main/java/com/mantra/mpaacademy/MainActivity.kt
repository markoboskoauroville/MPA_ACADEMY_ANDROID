package com.mantra.mpaacademy

import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.*
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var fBody: Typeface; private lateinit var fBodyM: Typeface; private lateinit var fCond: Typeface
    private lateinit var fCondS: Typeface; private lateinit var fMono: Typeface
    private val cats = ArrayList<Pair<String, List<Exercise>>>()
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private fun dpf(v: Float) = v * d

    // state kept across rotation
    private var cur: Exercise? = null
    private var speed = 4; private var voice = false; private var notesOn = false
    private var tele = true                       // TELEPROMPTER is the default (requests 217-221, 4/5 of 10.11.2026)
    private var player: Player? = null
    private var padL = 0; private var padR = 0; private var lastW = -1

    // the player screen's views
    private var sv: ScrollView? = null
    private var stage: FrameLayout? = null
    private var pat: View? = null
    private var playV: PlayView? = null
    private var cells = ArrayList<CellsView>(); private var labs = ArrayList<TextView>(); private var labX = 0
    private var anim: ValueAnimator? = null
    private var want = 0f                         // where the play button wants to be, in stage pixels (before clamping)
    private var curRow = 0; private var curLine = 0
    private var showRow = 0                       // request 12: only this phrase (its two lines) is on screen; the rest is hidden
    private fun cellFor(r: Int) = if (r == showRow) cells.getOrNull(0) else null
    private var ticker: Runnable? = null
    private var lr = -1; private var lj = -1; private var lastRow = -1; private var lastLine = -1

    private fun tf(id: Int) = resources.getFont(id)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        fBody = tf(R.font.barlow_regular); fBodyM = tf(R.font.barlow_medium); fCond = tf(R.font.barlow_condensed_bold)
        fCondS = tf(R.font.barlow_condensed_semibold); fMono = tf(R.font.plex_mono)
        tele = getPreferences(MODE_PRIVATE).getBoolean("tele", true)
        val j = JSONObject(assets.open("exercises.json").bufferedReader().readText())
        val ca = j.getJSONArray("categories")
        for (i in 0 until ca.length()) {
            val c = ca.getJSONObject(i); val ea = c.getJSONArray("exercises"); val l = ArrayList<Exercise>()
            for (k in 0 until ea.length()) {
                val e = ea.getJSONObject(k); val ra = e.getJSONArray("rows")
                l.add(Exercise(e.getString("code"), e.getString("name"), e.getInt("base"), e.getInt("loops"), e.getString("notes"),
                    (0 until ra.length()).map { r -> val x = ra.getJSONArray(r); (0 until x.length()).map { x.getString(it) } }))
            }
            l.forEach { it.loops = getPreferences(MODE_PRIVATE).getInt("loops", it.loops) }
            cats.add(c.getString("name") to l)
        }
        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, w ->
            val i = w.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            padL = i.left + dp(16); padR = i.right + dp(16)
            v.setPadding(padL, i.top + dp(8), padR, i.bottom + dp(16))
            val W = (resources.configuration.screenWidthDp * d).toInt()
            if (W != lastW) { lastW = W; build() }
            w
        }
        if (intent.getBooleanExtra("pin", false)) pinIcon()
    }

    /** Asks the launcher to put this app's icon on the home screen (adb: am start --ez pin true); the launcher shows its own Add dialog. */
    private fun pinIcon() {
        val sm = getSystemService(android.content.pm.ShortcutManager::class.java)
        if (!sm.isRequestPinShortcutSupported) return
        val i = android.content.Intent(this, MainActivity::class.java).setAction(android.content.Intent.ACTION_MAIN)
        val info = android.content.pm.ShortcutInfo.Builder(this, "mpa-home")
            .setShortLabel("MPA Academy").setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher)).setIntent(i).build()
        sm.requestPinShortcut(info, null)
    }

    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); root.requestApplyInsets() }

    // ------------------------------------------------------------------ pieces in the site's style
    private fun label(s: String) = TextView(this).apply { text = s.uppercase(); textSize = 13f; typeface = fCondS; setTextColor(DIM); letterSpacing = 0.08f }

    private fun pill(s: String, on: Boolean, f: () -> Unit) = TextView(this).apply {   // .tpbtn
        text = s.uppercase(); textSize = 13f; typeface = fCondS; letterSpacing = 0.08f; gravity = Gravity.CENTER
        setTextColor(AMBER); setPadding(dp(14), dp(4), dp(14), dp(4)); minHeight = dp(48); minimumWidth = dp(48)
        background = pressBg(d, AMBER, AMBER, 24f, false)
        alpha = if (on) 1f else 0.55f
        setOnTouchListener { v, e -> (v as TextView).setTextColor(if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) BG else AMBER); if (e.actionMasked == android.view.MotionEvent.ACTION_UP || e.actionMasked == android.view.MotionEvent.ACTION_CANCEL) v.setTextColor(AMBER); false }
        setOnClickListener { f() }
    }

    private fun pipe(s: CharSequence, on: Boolean, f: () -> Unit) = TextView(this).apply {   // .pipes button
        text = s; textSize = 18f; typeface = fCondS; letterSpacing = 0.05f; gravity = Gravity.CENTER
        setTextColor(if (on) AMBER2 else DIM); setPadding(dp(6), dp(4), dp(6), dp(4)); minHeight = dp(48); minimumWidth = dp(40)
        background = pressBg(d, 0x33F59E0B, 0, 6f)
        setOnClickListener { f() }
    }
    private fun bar(s: String = "|") = TextView(this).apply { text = s; textSize = 18f; typeface = fCondS; setTextColor(SLATE); setPadding(dp(4), 0, dp(4), 0); gravity = Gravity.CENTER; minHeight = dp(48) }
    private fun frac(a: Int, b: Int): CharSequence {
        val s = SpannableString("$a⁄$b")
        s.setSpan(SuperscriptSpan(), 0, a.toString().length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.7f), 0, a.toString().length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(SubscriptSpan(), s.length - b.toString().length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.7f), s.length - b.toString().length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }
    private fun codeTag(s: String) = TextView(this).apply {
        text = s; textSize = 13f; typeface = fMono; setTextColor(GREEN); letterSpacing = 0.06f; setPadding(dp(6), dp(2), dp(6), dp(2))
        background = pressBg(d, 0, GREEN, 4f)
    }

    // ------------------------------------------------------------------ the top bar: MPA ACADEMY · speeds · loops · portrait/landscape (Marko 10.11.2026)
    private fun orientPill(): View {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return pill(if (landscape) "Portrait" else "Landscape", true) {
            requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }.apply { alpha = 1f; contentDescription = "Switch to " + (if (landscape) "portrait" else "landscape") }
    }
    private fun titleView(e: Exercise? = null) = TextView(this).apply {
        // request 12: the current phrase ("6+2") sits next to the title
        val lab = e?.let { phrInfo(it.rows).getOrNull(showRow)?.lab }
        val s = SpannableString(if (lab.isNullOrEmpty()) "MPA ACADEMY" else "MPA ACADEMY   $lab")
        if (!lab.isNullOrEmpty()) {
            s.setSpan(android.text.style.ForegroundColorSpan(AMBER), s.length - lab.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(RelativeSizeSpan(1.35f), s.length - lab.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text = s; textSize = 18f; typeface = fCond; setTextColor(INK); letterSpacing = 0.06f; minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL; background = pressBg(d, 0x33F59E0B, 0, 6f); setOnClickListener { settings() } }
    private fun speedsView(e: Exercise): View {   // the site's speed control: 16/1 | 16/2 | 16/4 | 16/8 | 16/16 | 16/32, default 16/4
        val f = Flow(this, 0, 0)
        for ((k, v) in listOf(1, 2, 4, 8, 16, 32).withIndex()) {
            if (k > 0) f.addView(bar().apply { setPadding(0, 0, 0, 0) })
            f.addView(pipe(frac(16, v), v == speed) { speed = v; openPlayer(e, true); restartIfPlaying() }.apply { setPadding(dp(2), dp(4), dp(2), dp(4)); minimumWidth = dp(34); textSize = 16f })
        }
        return f
    }
    private fun loopsView(e: Exercise): View {    // repetitions of every part, changeable live (0 = forever)
        val f = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val n = TextView(this).apply { textSize = 18f; typeface = fCond; setTextColor(AMBER2); gravity = Gravity.CENTER }
        val lab = TextView(this).apply { text = "LOOPS"; textSize = 10f; typeface = fCondS; setTextColor(DIM); letterSpacing = 0.08f; gravity = Gravity.CENTER }
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumWidth = dp(46); addView(lab); addView(n) }
        fun show() { n.text = if (e.loops == 0) "\u221E" else "${e.loops}" }
        fun set(v: Int) { e.loops = v.coerceIn(0, 99); getPreferences(MODE_PRIVATE).edit().putInt("loops", e.loops).apply(); show() }
        show()
        f.addView(pipe("\u2212", false) { set(if (e.loops == 0) 99 else e.loops - 1) }.apply { textSize = 22f; minimumWidth = dp(40) })
        f.addView(mid)
        f.addView(pipe("+", false) { set(if (e.loops >= 99) 0 else e.loops + 1) }.apply { textSize = 22f; minimumWidth = dp(40) })
        return f
    }
    private fun topBar(e: Exercise? = null): View {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        row.addView(titleView(e), LinearLayout.LayoutParams(0, -2, 1f))
        if (e != null && landscape) {
            row.addView(speedsView(e), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(16) })
            row.addView(loopsView(e), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(16) })
        }
        row.addView(orientPill())
        col.addView(row)
        if (e != null && !landscape) {
            val r2 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r2.addView(speedsView(e), LinearLayout.LayoutParams(0, -2, 1f))
            r2.addView(loopsView(e), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            col.addView(r2)
        }
        col.addView(View(this).apply { setBackgroundColor(LINE); layoutParams = LinearLayout.LayoutParams(-1, 1) })
        return col
    }

    // ------------------------------------------------------------------ settings (tap the title): one switch and, at the bottom, the copyright line
    private fun settings() {
        inSettings = true; stopPlay(); root.removeAllViews(); sv = null
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(topBar())
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(16), 0, dp(16)) }
        col.addView(label("Settings"))
        val r = Flow(this, dp(12), 0)
        r.addView(label("Prompter (the page follows the row being played):").apply { minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL })
        r.addView(pill(if (tele) "On" else "Off", tele) { tele = !tele; getPreferences(MODE_PRIVATE).edit().putBoolean("tele", tele).apply(); settings() })
        col.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        col.addView(label("Version ${packageManager.getPackageInfo(packageName, 0).versionName}").apply { setPadding(0, dp(12), 0, 0) })
        col.addView(pill("Back", true) { if (cur != null) openPlayer(cur!!) else home() }.apply { alpha = 1f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
        box.addView(ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(-1, 0, 1f))
        box.addView(TextView(this).apply { text = "\u00A9 2026 Mantra Productions"; textSize = 13f; typeface = fBody; setTextColor(DIM); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4)) }, LinearLayout.LayoutParams(-1, -2))
        root.addView(box)
    }

    private var inSettings = false
    private fun build() { if (inSettings) settings() else if (cur == null) home() else openPlayer(cur!!, keepScroll = true) }

    // ------------------------------------------------------------------ home: the Lab's exercises as collections
    private fun home() {
        inSettings = false; stopPlay(); cur = null; root.removeAllViews(); sv = null
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(topBar())
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(16), 0, dp(24)) }
        col.addView(label("Exercise Lab"))
        for ((name, list) in cats) {
            col.addView(TextView(this).apply { text = name.uppercase(); textSize = 18f; typeface = fCond; setTextColor(AMBER); letterSpacing = 0.06f; setPadding(0, dp(20), 0, dp(6)) })
            for (e in list) {
                val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56); setPadding(dp(12), dp(8), dp(12), dp(8)); isClickable = true
                    background = pressBg(d, 0x33F59E0B, LINE, 6f); setOnClickListener { openPlayer(e) } }
                r.addView(codeTag(e.code))
                r.addView(TextView(this).apply { text = e.name.uppercase(); textSize = 18f; typeface = fCond; setTextColor(INK); letterSpacing = 0.06f; setPadding(dp(10), 0, 0, 0) })
                col.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        box.addView(ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(box)
    }

    // ------------------------------------------------------------------ the exercise page, laid out like the site's
    private fun openPlayer(e: Exercise, keepScroll: Boolean = false) {
        val was = sv?.scrollY ?: 0
        inSettings = false
        val wasPlaying = player != null
        if (e !== cur) showRow = 0                    // a new exercise starts at its first phrase
        if (!keepScroll) { stopPlay(); curRow = showRow; curLine = 0; lastRow = -1; lastLine = -1 } else { ticker?.let { root.removeCallbacks(it) }; ticker = null }
        if (keepScroll) { lr = -1; lj = -1; lastRow = -1; lastLine = -1 }
        cur = e; root.removeAllViews()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(topBar(e))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(40)) }
        // head: back, code, title, PROMPTER, NOTES
        val head = Flow(this, dp(10), dp(4))
        head.addView(pipe("◀", true) { home() }.apply { setTextColor(AMBER); textSize = 16f })
        head.addView(codeTag(e.code).apply { translationY = dpf(0f) })
        head.addView(TextView(this).apply { text = e.name.uppercase(); textSize = 18f; typeface = fCond; setTextColor(INK); letterSpacing = 0.06f; minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL })
        head.addView(pill("Prompter", tele) { tele = !tele; getPreferences(MODE_PRIVATE).edit().putBoolean("tele", tele).apply(); openPlayer(e, true) })
        head.addView(pill(if (notesOn) "Cells" else "Notes", notesOn) { notesOn = !notesOn; openPlayer(e, true) })
        col.addView(head)
        // base speed | sound
        val r1 = Flow(this, dp(8), 0)
        r1.addView(label("Base speed:").apply { minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL })
        r1.addView(TextView(this).apply { text = "${e.base}"; textSize = 18f; typeface = fCond; setTextColor(INK); minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL })
        r1.addView(bar("||"))
        r1.addView(pipe("VOICE", voice) { voice = true; openPlayer(e, true); restartIfPlaying() })
        r1.addView(bar())
        r1.addView(pipe("DRUM", !voice) { voice = false; openPlayer(e, true); restartIfPlaying() })
        col.addView(r1)
        // the pattern (or the notes)
        stage = FrameLayout(this); cells = ArrayList(); labs = ArrayList(); playV = null; pat = null
        val st = stage!!
        if (notesOn) {
            col.addView(TextView(this).apply { text = e.notes; textSize = 16f; typeface = fBody; setTextColor(INK); setLineSpacing(0f, 1.3f); setPadding(0, dp(16), 0, 0) })
            stopPlay()
        } else {
            val screenW = (resources.configuration.screenWidthDp * d).toInt() - padL - padR
            val wide = false                                               // request 11: every phrase is two lines, one bar per line, in both orientations
            val gutter = if (wide) dp(140) else dp(80)
            labX = if (wide) dp(52) else 0
            val rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(gutter, dp(14), 0, 0) }
            val info = phrInfo(e.rows)
            for ((ri, row) in e.rows.withIndex()) {
                if (ri != showRow) continue                                // request 12: one phrase on screen, the rest hidden
                if (info[ri].head) rowsBox.addView(TextView(this).apply { text = "Reverse dynamics:"; textSize = 18f; typeface = fBody; setTextColor(INK); letterSpacing = 0.04f; setPadding(0, dp(22), 0, dp(6)) })
                val cv = CellsView(this, row, spoons(row), if (wide) 16 else 8, fBody, fMono)
                cells.add(cv)
                rowsBox.addView(cv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                labs.add(TextView(this).apply { text = info[ri].lab; textSize = if (wide) 34f else 30f; typeface = fBody; setTextColor(AMBER); gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false })
            }
            pat = rowsBox
            st.addView(rowsBox, FrameLayout.LayoutParams(-1, -2))
            // request 13: the phrase label ("3+5") is only in the title now, not beside the cells
            labs.clear()
            val pv = PlayView(this).apply { contentDescription = "Play"; setOnClickListener { togglePlay(e) } }
            st.addView(pv, FrameLayout.LayoutParams(dp(44), dp(44)))
            playV = pv
            pv.on = player != null
            col.addView(st)
        }
        val scroll = ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }
        sv = scroll
        box.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(box)
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> if (anim == null) applyBtn() }
        scroll.post {
            if (keepScroll) scroll.scrollTo(0, was)
            setBtn(want = btnWantFor(curRow, curLine)); placeLabels(); if (wasPlaying && player != null) startTicker()
        }
    }

    private fun restartIfPlaying() { val e = cur ?: return; if (player != null) { stopPlay(); startPlay(e) } }

    // ------------------------------------------------------------------ the teleprompter (site: lessons.js play(), moveTo(), midScroll(), placePlay(), clampTop())
    // "the reading line is the vertical MIDDLE of the visible viewport; the play button is always clamped inside the visible area;
    //  prompter on scrolls the current row's centre to the middle line (smooth, 380 ms); off only scrolls when the row is out of view."
    private fun yIn(parent: View, v: View): Float { var y = 0f; var x: View? = v; while (x != null && x !== parent) { y += x.top; x = x.parent as? View }; return y }
    private fun rowCenter(r: Int, line: Int = 0): Float { val s = stage ?: return 0f; val c = cellFor(r) ?: return 0f; return yIn(s, c) + c.lineCenter(line) }   // stage px: the centre of the LINE being played
    private fun btnWantFor(r: Int, line: Int = 0) = rowCenter(r, line) - dpf(22f)
    private fun clampBtn(raw: Float): Float {      // inside the viewport and inside its own pattern (site: clampTop)
        val s = sv ?: return raw; val st = stage ?: return raw
        val top = s.scrollY - stageTop()
        val lo = maxOf(0f, top + dpf(4f)); val hi = minOf(st.height - dpf(44f), top + s.height - dpf(48f))
        return if (hi < lo) (if (top < 0) 0f else st.height - dpf(44f)) else raw.coerceIn(lo, hi)
    }
    /** The row numbers are big in the front slot; the play marker takes the slot of the line being played and the number moves to the other line of that row (they swap). */
    private fun placeLabels() {
        val s = stage ?: return
        for ((i, cv) in cells.withIndex()) {
            val r = showRow
            val lab = labs.getOrNull(i) ?: continue
            val line = if (r == curRow && cv.cols < 16 && cv.lineCount() > 1 && curLine == 0) 1 else 0
            lab.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            lab.translationX = labX.toFloat()
            lab.translationY = yIn(s, cv) + cv.lineCenter(line) - lab.measuredHeight / 2f
        }
    }
    private fun stageTop(): Float = (stage?.top ?: 0).toFloat()
    private fun setBtn(want: Float) { this.want = want; applyBtn() }
    private fun applyBtn() { val b = playV ?: return; b.translationY = Math.round(clampBtn(want)).toFloat() }
    private fun midScroll(r: Int, smooth: Boolean, line: Int = 0) {
        val s = sv ?: return; val c = rowCenter(r, line) + stageTop() - s.scrollY   // viewport y of the row centre
        val dy = c - s.height / 2f; if (Math.abs(dy) > 1f) { if (smooth) s.smoothScrollBy(0, dy.toInt()) else s.scrollBy(0, dy.toInt()) }
    }
    private fun moveTo(r: Int, line: Int) {
        val s = sv ?: return; anim?.cancel()
        val b0 = want; val b1 = btnWantFor(r, line); val s0 = s.scrollY
        val c = rowCenter(r, line) + stageTop() - s0; val H = s.height
        val dy = if (tele) c - H / 2f else (if (c < dpf(30f) || c > H - dpf(30f)) c - H / 2f else 0f)
        val maxS = (s.getChildAt(0).height - H).coerceAtLeast(0)
        val target = (s0 + dy).coerceIn(0f, maxS.toFloat())
        if (Math.abs(b1 - b0) < 2f && Math.abs(target - s0) < 2f) { want = b1; return }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380; interpolator = DecelerateInterpolator(3f)
            addUpdateListener { a -> val k = a.animatedValue as Float
                s.scrollTo(0, (s0 + (target - s0) * k).toInt()); want = b0 + (b1 - b0) * k; applyBtn() }
            addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(x: android.animation.Animator) { anim = null; want = b1; applyBtn() } })
            start()
        }
    }

    // ------------------------------------------------------------------ playing
    private fun togglePlay(e: Exercise) { if (player != null) { stopPlay(); lightsOff() } else startPlay(e) }

    private fun startPlay(e: Exercise) {
        val p = Player(e, Bank(this, if (voice) "voice" else "drum"), speed, showRow) {}   // request 12: continue in sequence from the phrase on screen
        player = p; playV?.on = true; curRow = showRow; curLine = 0; lastRow = -1; lastLine = -1; lr = -1; lj = -1
        placeLabels(); setBtn(btnWantFor(showRow)); if (tele) { midScroll(showRow, false); applyBtn() }     // site: placePlay(el,0); midScroll(el,0,false)
        p.start(); startTicker()
    }

    private fun startTicker() {
        val p = player ?: return; val e = cur ?: return
        val tick = object : Runnable {
            override fun run() {
                if (player !== p) return
                p.heard()?.let { (r, j, _) ->
                    if (r != showRow) {                     // request 12: the next phrase replaces the one on screen, in sequence
                        showRow = r; curRow = r; curLine = 0; lastLine = -1; lr = -1; lj = -1
                        openPlayer(e, keepScroll = true); return
                    }
                    if (r != lr || j != lj) {
                        cellFor(lr)?.lit = -1
                        cellFor(r)?.lit = j; lr = r; lj = j
                    }
                    val cv0 = cellFor(r)
                    if (cv0 != null && cv0.height > 0 && labs.firstOrNull()?.translationY == 0f) placeLabels()   // after a phrase change the label is placed once the new phrase is laid out
                    val line = j / (cv0?.cols ?: 8)
                    lastRow = r
                    if (r != curRow || line != lastLine) { curRow = r; curLine = line; lastLine = line; placeLabels(); moveTo(r, line) }
                }
                root.postDelayed(this, 16)
            }
        }
        ticker = tick; root.post(tick)
    }

    private fun lightsOff() { cells.forEach { it.lit = -1 }; playV?.on = false }
    private fun stopPlay() { player?.stop(); player = null; ticker?.let { root.removeCallbacks(it) }; ticker = null; anim?.cancel(); anim = null; playV?.on = false; lr = -1; lj = -1 }

    @Deprecated("back")
    override fun onBackPressed() { if (inSettings) { if (cur != null) openPlayer(cur!!) else home() } else if (cur != null) home() else super.onBackPressed() }
    override fun onPause() { super.onPause(); if (player != null) { stopPlay(); lightsOff() } }
}
