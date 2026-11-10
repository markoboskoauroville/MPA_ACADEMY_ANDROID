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
    private var cells = ArrayList<CellsView>(); private var reps = ArrayList<TextView>()
    private var anim: ValueAnimator? = null
    private var want = 0f                         // where the play button wants to be, in stage pixels (before clamping)
    private var curRow = 0
    private var ticker: Runnable? = null
    private var lr = -1; private var lj = -1; private var lastRow = -1

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
            cats.add(c.getString("name") to l)
        }
        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, w ->
            val i = w.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            padL = i.left + dp(16); padR = i.right + dp(16)
            v.setPadding(padL, i.top + dp(8), padR, i.bottom + dp(16))
            val W = resources.displayMetrics.widthPixels
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

    // ------------------------------------------------------------------ the top bar: name left, orientation switch upper right
    private fun topBar(): View {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        row.addView(TextView(this).apply { text = "MPA ACADEMY"; textSize = 18f; typeface = fCond; setTextColor(INK); letterSpacing = 0.06f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        row.addView(pill(if (landscape) "Portrait" else "Landscape", true) {
            requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }.apply { alpha = 1f; contentDescription = "Switch to " + (if (landscape) "portrait" else "landscape") })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(row)
        col.addView(View(this).apply { setBackgroundColor(LINE); layoutParams = LinearLayout.LayoutParams(-1, 1) })
        return col
    }

    private fun build() { if (cur == null) home() else openPlayer(cur!!, keepScroll = true) }

    // ------------------------------------------------------------------ home: the Lab's exercises as collections
    private fun home() {
        stopPlay(); cur = null; root.removeAllViews(); sv = null
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
        val wasPlaying = player != null
        if (!keepScroll) { stopPlay(); curRow = 0; lastRow = -1 } else { ticker?.let { root.removeCallbacks(it) }; ticker = null }
        if (keepScroll) { lr = -1; lj = -1; lastRow = -1 }
        cur = e; root.removeAllViews()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(topBar())
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
        val r2 = Flow(this, dp(0), 0)
        r2.addView(label("Speed:").apply { minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, dp(8), 0) })
        for ((k, v) in listOf(1, 2, 4, 8, 16, 32).withIndex()) {
            if (k > 0) r2.addView(bar())
            r2.addView(pipe(frac(16, v), v == speed) { speed = v; openPlayer(e, true); restartIfPlaying() })
        }
        col.addView(r2)
        // the pattern (or the notes)
        stage = FrameLayout(this); cells = ArrayList(); reps = ArrayList(); playV = null; pat = null
        val st = stage!!
        if (notesOn) {
            col.addView(TextView(this).apply { text = e.notes; textSize = 16f; typeface = fBody; setTextColor(INK); setLineSpacing(0f, 1.3f); setPadding(0, dp(16), 0, 0) })
            stopPlay()
        } else {
            val gutter = dp(52)
            val availW = resources.displayMetrics.widthPixels - padL - padR - gutter
            val inlineCols = (availW - dp(110) - dp(60)) / 16 >= dp(36)       // landscape: label, 16 cells, counter on one line
            val rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(gutter, dp(14), 0, 0) }
            val info = phrInfo(e.rows)
            for ((ri, row) in e.rows.withIndex()) {
                if (info[ri].head) rowsBox.addView(TextView(this).apply { text = "Reverse dynamics:"; textSize = 18f; typeface = fBody; setTextColor(INK); letterSpacing = 0.04f; setPadding(0, dp(22), 0, dp(6)) })
                val cv = CellsView(this, row, spoons(row), if (inlineCols) 16 else 8, fBody, fMono)
                val lab = TextView(this).apply { text = info[ri].lab; textSize = 30f; typeface = fBody; setTextColor(AMBER); gravity = Gravity.END or Gravity.CENTER_VERTICAL }
                val rep = TextView(this).apply { textSize = 15f; typeface = fMono; setTextColor(AMBER); gravity = Gravity.END or Gravity.CENTER_VERTICAL }
                reps.add(rep); cells.add(cv)
                val line: View = if (inlineCols) LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(lab, LinearLayout.LayoutParams(dp(110), -2).apply { rightMargin = dp(8) })
                    addView(cv, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(rep, LinearLayout.LayoutParams(dp(60), -2))
                } else LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    val h = LinearLayout(this@MainActivity)
                    h.addView(lab.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, -2, 1f))
                    h.addView(rep, LinearLayout.LayoutParams(-2, -2))
                    addView(h); addView(cv, LinearLayout.LayoutParams(-1, -2))
                }
                rowsBox.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
            pat = rowsBox
            st.addView(rowsBox, FrameLayout.LayoutParams(-1, -2))
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
            setBtn(want = btnWantFor(if (player != null) curRow else 0)); if (wasPlaying && player != null) startTicker()
        }
    }

    private fun restartIfPlaying() { val e = cur ?: return; if (player != null) { stopPlay(); startPlay(e) } }

    // ------------------------------------------------------------------ the teleprompter (site: lessons.js play(), moveTo(), midScroll(), placePlay(), clampTop())
    // "the reading line is the vertical MIDDLE of the visible viewport; the play button is always clamped inside the visible area;
    //  prompter on scrolls the current row's centre to the middle line (smooth, 380 ms); off only scrolls when the row is out of view."
    private fun yIn(parent: View, v: View): Float { var y = 0f; var x: View? = v; while (x != null && x !== parent) { y += x.top; x = x.parent as? View }; return y }
    private fun rowCenter(r: Int): Float { val s = stage ?: return 0f; val c = cells.getOrNull(r) ?: return 0f; return yIn(s, c) + c.height / 2f }   // stage px
    private fun btnWantFor(r: Int) = rowCenter(r) - dpf(22f)
    private fun clampBtn(raw: Float): Float {      // inside the viewport and inside its own pattern (site: clampTop)
        val s = sv ?: return raw; val st = stage ?: return raw
        val top = s.scrollY - stageTop()
        val lo = maxOf(0f, top + dpf(4f)); val hi = minOf(st.height - dpf(44f), top + s.height - dpf(48f))
        return if (hi < lo) (if (top < 0) 0f else st.height - dpf(44f)) else raw.coerceIn(lo, hi)
    }
    private fun stageTop(): Float = (stage?.top ?: 0).toFloat()
    private fun setBtn(want: Float) { this.want = want; applyBtn() }
    private fun applyBtn() { val b = playV ?: return; b.translationY = Math.round(clampBtn(want)).toFloat() }
    private fun midScroll(r: Int, smooth: Boolean) {
        val s = sv ?: return; val c = rowCenter(r) + stageTop() - s.scrollY   // viewport y of the row centre
        val dy = c - s.height / 2f; if (Math.abs(dy) > 1f) { if (smooth) s.smoothScrollBy(0, dy.toInt()) else s.scrollBy(0, dy.toInt()) }
    }
    private fun moveTo(r: Int) {
        val s = sv ?: return; anim?.cancel()
        val b0 = want; val b1 = btnWantFor(r); val s0 = s.scrollY
        val c = rowCenter(r) + stageTop() - s0; val H = s.height
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
        val p = Player(e, Bank(this, if (voice) "voice" else "drum"), speed) {}
        player = p; playV?.on = true; curRow = 0; lastRow = -1; lr = -1; lj = -1
        setBtn(btnWantFor(0)); if (tele) { midScroll(0, false); applyBtn() }     // site: placePlay(el,0); midScroll(el,0,false)
        p.start(); startTicker()
    }

    private fun startTicker() {
        val p = player ?: return; val e = cur ?: return
        val tick = object : Runnable {
            override fun run() {
                if (player !== p) return
                p.heard()?.let { (r, j, rep) ->
                    if (r != lr || j != lj) {
                        if (lr in cells.indices) cells[lr].lit = -1
                        cells.getOrNull(r)?.lit = j; lr = r; lj = j
                    }
                    if (r != lastRow) {
                        if (lastRow in reps.indices) reps[lastRow].text = ""
                        lastRow = r; curRow = r; moveTo(r)
                    }
                    reps.getOrNull(r)?.text = "$rep / ${e.loops}"
                }
                root.postDelayed(this, 16)
            }
        }
        ticker = tick; root.post(tick)
    }

    private fun lightsOff() { cells.forEach { it.lit = -1 }; reps.forEach { it.text = "" }; playV?.on = false }
    private fun stopPlay() { player?.stop(); player = null; ticker?.let { root.removeCallbacks(it) }; ticker = null; anim?.cancel(); anim = null; playV?.on = false; lr = -1; lj = -1 }

    @Deprecated("back")
    override fun onBackPressed() { if (cur != null) home() else super.onBackPressed() }
    override fun onPause() { super.onPause(); if (player != null) { stopPlay(); lightsOff() } }
}
