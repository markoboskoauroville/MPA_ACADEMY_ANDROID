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
    private var speed = 4; private var notesOn = false   // request 18: drum sounds only, no voice, no switch
    private val prefs get() = getPreferences(MODE_PRIVATE)   // request 18: every setting is remembered between sessions
    private val RELEASES = "https://github.com/markoboskoauroville/MPA_ACADEMY_ANDROID/releases/latest"
    private var tele = true                       // TELEPROMPTER always on (request 14: no switch)
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
        tele = true
        speed = prefs.getInt("speed", 4).let { if (it in listOf(1, 2, 4, 8, 16, 32)) it else 4 }
        when (prefs.getInt("orient", 0)) { 1 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; 2 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        val j = JSONObject(assets.open("exercises.json").bufferedReader().readText())
        val ca = j.getJSONArray("categories")
        for (i in 0 until ca.length()) {
            val c = ca.getJSONObject(i); val ea = c.getJSONArray("exercises"); val l = ArrayList<Exercise>()
            for (k in 0 until ea.length()) {
                val e = ea.getJSONObject(k); val ra = e.getJSONArray("rows")
                l.add(Exercise(e.getString("code"), e.getString("name"), e.getInt("base"), e.getInt("loops"), e.getString("notes"),
                    (0 until ra.length()).map { r -> val x = ra.getJSONArray(r); (0 until x.length()).map { x.getString(it) } }))
            }
            l.forEach { it.loops = prefs.getInt("loops", it.loops) }
            cats.add(c.getString("name") to l)
        }
        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, w ->
            val i = w.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            padL = i.left + dp(16); padR = i.right + dp(16)
            v.setPadding(padL, i.top + dp(8), padR, i.bottom + dp(16))
            val W = (resources.configuration.screenWidthDp * d).toInt()
            if (W != lastW) { lastW = W; if (firstBuild && cur == null && cats.sumOf { it.second.size } == 1) { cur = cats[0].second[0]; showRow = prefs.getInt("row_" + cur!!.code, 0).coerceIn(0, cur!!.rows.size - 1) }; firstBuild = false; build() }
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
    private fun orientPill(): View {   // request 19: a plain rectangle in the screen's orientation; a tap turns the screen
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return OrientView(this, landscape).apply {
            contentDescription = "Switch to " + (if (landscape) "portrait" else "landscape")
            setOnClickListener {
                requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                prefs.edit().putInt("orient", if (landscape) 2 else 1).apply()
            }
        }
    }
    private fun titleView(e: Exercise? = null) = TextView(this).apply {
        // request 12: the current phrase ("6+2") sits next to the title
        val lab = e?.let { phrInfo(it.rows, titleGroups(it.name)).getOrNull(showRow)?.lab }
        val head = e?.name?.uppercase() ?: "MPA ACADEMY"              // request 14: the exercise's name is the title in the player
        val s = SpannableString(if (lab.isNullOrEmpty()) head else "$head   $lab")
        if (!lab.isNullOrEmpty()) {
            s.setSpan(android.text.style.ForegroundColorSpan(AMBER), s.length - lab.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(RelativeSizeSpan(1.35f), s.length - lab.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text = s; textSize = 18f; typeface = fCond; setTextColor(INK); letterSpacing = 0.06f; minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL; background = pressBg(d, 0x33F59E0B, 0, 6f); setOnClickListener { settings() } }
    private fun fracView(n: Int, big: Boolean = true) = LinearLayout(this).apply {   // 16 over a straight line over n
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        fun num(v: Int) = TextView(this@MainActivity).apply { text = "$v"; textSize = if (big) 15f else 17f; typeface = fCond; setTextColor(AMBER2); gravity = Gravity.CENTER; includeFontPadding = false }
        addView(num(16))
        addView(View(this@MainActivity).apply { setBackgroundColor(AMBER2) }, LinearLayout.LayoutParams(dp(22), dp(2)).apply { topMargin = dp(2); bottomMargin = dp(2) })
        addView(num(n))
    }
    private fun speedsView(e: Exercise): View {   // request 19: the speed is a drop-down menu, 16/1 16/2 16/4 16/8 16/16 16/32
        val all = listOf(1, 2, 4, 8, 16, 32)
        val f = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), 0, dp(4), 0); minimumHeight = dp(48); background = pressBg(d, 0x33F59E0B, 0, 6f); contentDescription = "Speed" }
        f.addView(fracView(speed))
        f.addView(TextView(this).apply { text = "\u25BE"; textSize = 14f; setTextColor(AMBER2); setPadding(dp(4), 0, 0, 0) })
        f.setOnClickListener { anchor ->
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)); background = pressBg(d, BG2, LINE, 8f, true) }
            val pw = android.widget.PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
            for (n in all) list.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER; setPadding(dp(18), dp(6), dp(18), dp(6)); minimumHeight = dp(52)
                background = pressBg(d, 0x33F59E0B, 0, 6f, n == speed, Color.TRANSPARENT)
                addView(fracView(n, false))
                setOnClickListener { pw.dismiss(); if (n != speed) { speed = n; prefs.edit().putInt("speed", speed).apply(); openPlayer(e, true); restartIfPlaying() } }
            })
            pw.elevation = dpf(8f); pw.showAsDropDown(anchor, 0, dp(2))
        }
        return f
    }
    private fun loopsView(e: Exercise): View {    // repetitions of every part, changeable live (0 = forever)
        val f = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val n = TextView(this).apply { textSize = 18f; typeface = fCond; setTextColor(AMBER2); gravity = Gravity.CENTER }
        val lab = TextView(this).apply { text = if (compact) "" else "LOOPS"; textSize = if (compact) 1f else 10f; typeface = fCondS; setTextColor(DIM); letterSpacing = 0.08f; gravity = Gravity.CENTER }
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumWidth = dp(if (compact) 22 else 46); if (!compact) addView(lab); addView(n) }
        fun show() { n.text = if (e.loops == 0) "\u221E" else "${e.loops}" }
        fun set(v: Int) { e.loops = v.coerceIn(0, 99); prefs.edit().putInt("loops", e.loops).apply(); show() }
        show()
        f.addView(pipe("\u2212", false) { set(if (e.loops == 0) 99 else e.loops - 1) }.apply { textSize = 22f; minimumWidth = dp(if (compact) 26 else 40); setPadding(0, dp(4), 0, dp(4)) })
        f.addView(mid)
        f.addView(pipe("+", false) { set(if (e.loops >= 99) 0 else e.loops + 1) }.apply { textSize = 22f; minimumWidth = dp(if (compact) 26 else 40); setPadding(0, dp(4), 0, dp(4)) })
        return f
    }
    private fun skipsView(e: Exercise): View {     // request 18: skip buttons like a cassette deck's, the phrase title between them
        val info = phrInfo(e.rows, titleGroups(e.name)); val cur = info.getOrNull(showRow)
        val f = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun skip(k: Int) { val n = (showRow + k).coerceIn(0, e.rows.size - 1); if (n != showRow) { setRow(e, n); openPlayer(e, true); restartIfPlaying() } }
        val w = dp(if (compact) 28 else 40)
        f.addView(SkipView(this, false, showRow > 0).apply { setOnClickListener { skip(-1) }; contentDescription = "Previous exercise" }, LinearLayout.LayoutParams(w, dp(44)))
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; minimumWidth = dp(if (compact) 26 else 44) }
        mid.addView(TextView(this).apply { text = cur?.lab ?: ""; textSize = if (compact) 20f else 24f; typeface = fCond; setTextColor(AMBER); gravity = Gravity.CENTER; includeFontPadding = false })
        // reverse dynamics is written here, at the top, never down among the cells
        f.addView(mid)
        f.addView(SkipView(this, true, showRow < e.rows.size - 1).apply { setOnClickListener { skip(1) }; contentDescription = "Next exercise" }, LinearLayout.LayoutParams(w, dp(44)))
        // request 19: reverse dynamics beside the switcher, in parentheses
        if (cur?.rev == true) f.addView(TextView(this).apply { text = if (compact) "(reverse\ndynamics)" else "(reverse dynamics)"; textSize = if (compact) 11f else 14f; typeface = fCondS; setTextColor(AMBER); gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; setLineSpacing(0f, 0.9f); setPadding(dp(2), 0, 0, 0) })
        return f
    }
    private fun setRow(e: Exercise, r: Int) { showRow = r; prefs.edit().putInt("row_" + e.code, r).apply() }
    private fun versionView() = TextView(this).apply {   // upper right: the version; a tap opens the latest release on GitHub
        text = "v" + packageManager.getPackageInfo(packageName, 0).versionName; textSize = 12f; typeface = fMono; setTextColor(DIM); gravity = Gravity.CENTER
        setPadding(dp(6), dp(2), dp(2), dp(2)); minHeight = dp(44)
        setOnClickListener { try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(RELEASES))) } catch (_: Exception) { Toast.makeText(this@MainActivity, "No browser", Toast.LENGTH_SHORT).show() } }
    }
    private fun topBar(e: Exercise? = null): View {
        // request 16: the upper frame is ONE line: phrase label, speeds, loops, voice | drum, portrait/landscape (no exercise title)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        if (e == null) {
            row.addView(titleView(), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(orientPill(), LinearLayout.LayoutParams(dp(44), dp(44)))
        } else {
            val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE   // portrait: compact controls so the line stays ONE line
            compact = portrait
            row.addView(skipsView(e))
            row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            row.addView(speedsView(e))
            row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            row.addView(loopsView(e))
            row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            row.addView(orientPill(), LinearLayout.LayoutParams(dp(if (compact) 36 else 44), dp(44)))
        }
        row.addView(versionView())
        col.addView(row)
        col.addView(View(this).apply { setBackgroundColor(LINE); layoutParams = LinearLayout.LayoutParams(-1, 1) })
        return col
    }
    private var compact = false
    private fun shrink(v: View) { if (v is ViewGroup) for (i in 0 until v.childCount) { val c = v.getChildAt(i); if (c is TextView) { c.minimumWidth = dp(if (compact) 22 else 26); c.setPadding(dp(1), 0, dp(1), 0); if (compact) { if (c.text.toString() == "|") c.visibility = View.GONE else c.textSize = 12f } } } }

    // ------------------------------------------------------------------ settings (tap the title): one switch and, at the bottom, the copyright line
    private fun settings() {
        inSettings = true; stopPlay(); root.removeAllViews(); sv = null
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(topBar())
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(16), 0, dp(16)) }
        col.addView(label("Settings"))
        val r = Flow(this, dp(12), 0)
        col.addView(pill("Back", true) { if (cur != null) openPlayer(cur!!) else home() }.apply { alpha = 1f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
        box.addView(ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(-1, 0, 1f))
        box.addView(TextView(this).apply { text = "\u00A9 2026 Mantra Productions"; textSize = 13f; typeface = fBody; setTextColor(DIM); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4)) }, LinearLayout.LayoutParams(-1, -2))
        root.addView(box)
    }

    private var inSettings = false
    private var firstBuild = true   // request 19: with one exercise in the app, it opens straight away
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
                // request 15: no exercise code (PH01) anywhere in the interface
                r.addView(TextView(this).apply { text = e.name.uppercase(); textSize = 18f; typeface = fCond; setTextColor(INK); letterSpacing = 0.06f })
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
        if (e !== cur) showRow = prefs.getInt("row_" + e.code, 0).coerceIn(0, e.rows.size - 1)                    // a new exercise starts at its first phrase
        if (!keepScroll) { stopPlay(); curRow = showRow; curLine = 0; lastRow = -1; lastLine = -1 } else { ticker?.let { root.removeCallbacks(it) }; ticker = null }
        if (keepScroll) { lr = -1; lj = -1; lastRow = -1; lastLine = -1 }
        cur = e; root.removeAllViews()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(topBar(e))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(40)) }
        // request 14: the lower frame shows only the exercise (no head, no prompter/notes, base speed fixed and hidden)
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
            val info = phrInfo(e.rows, titleGroups(e.name))
            val tg = titleGroups(e.name)
            for ((ri, row) in e.rows.withIndex()) {
                if (ri != showRow) continue                                // request 12: one phrase on screen, the rest hidden
                val cv = CellsView(this, row, spoons(row, tg), if (wide) 16 else 8, fBody, fMono)
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
        val p = Player(e, Bank(this, "drum"), speed, showRow) {}   // request 12: continue in sequence from the phrase on screen
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
                        setRow(e, r); curRow = r; curLine = 0; lastLine = -1; lr = -1; lj = -1
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
