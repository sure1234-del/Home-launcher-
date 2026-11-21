package com.mission.launcher

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

/** Home layout model: ordered pages, each an ordered list of card ids ("w:<id>" = system widget). */
object HomeLayout {
    private const val KEY = "home_layout"
    fun defaults(): JSONArray = JSONArray()
        .put(page("Today", "header", "countdown", "upcoming", "plan", "water", "weekly", "important"))
        .put(page("Progress", "nexttopic", "syl3d", "prelims", "mains", "mockstats", "zite"))
        .put(page("Study", "timer", "history", "focus", "revision", "ai", "notes", "usage", "tools", "links"))
    private fun page(name: String, vararg ids: String) = JSONObject().put("name", name).put("cards", JSONArray(ids.toList()))
    fun load(): JSONArray = try { val s = Store.s(KEY); if (s.isBlank()) defaults() else JSONArray(s).also { if (it.length() == 0) return defaults() } } catch (e: Exception) { defaults() }
    fun save(a: JSONArray) { Store.put(KEY, a.toString()) }
    fun reset() { Store.put(KEY, null) }
    fun cards(p: JSONObject): MutableList<String> { val a = p.optJSONArray("cards") ?: JSONArray(); return (0 until a.length()).map { a.optString(it) }.toMutableList() }
    fun setCards(p: JSONObject, l: List<String>) { p.put("cards", JSONArray(l)) }
}

class MainActivity : Activity(), Host {
    override val act: Activity get() = this
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var pager: Pager
    private lateinit var dots: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var editBar: LinearLayout
    private lateinit var content: FrameLayout
    private var drawer: DrawerView? = null
    private var gateView: View? = null
    private var editing = false
    private var topInset = 0; private var botInset = 0
    private val scrollY = HashMap<Int, Int>()
    private lateinit var widgetHost: AppWidgetHost
    private val REQ_PICK = 71; private val REQ_BIND = 72; private val REQ_CONF = 73; private val REQ_NOTIF = 74
    private var pendingWidget = -1
    private var layout = JSONArray()
    private val listener: () -> Unit = { scheduleRefresh() }
    private val refreshRun = Runnable { if (pager.busy) scheduleRefresh(600) else if (currentFocus !is EditText) rebuild() else scheduleRefresh(2500) }

    private val pkgReceiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { Apps.load(this@MainActivity) { drawer?.refresh(); buildDock() } } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Boot.ensure(this)
        Migrate.run(this)
        systemBars()
        widgetHost = AppWidgetHost(this, 1024)
        root = FrameLayout(this); setContentView(root)
        root.setOnApplyWindowInsetsListener { _, ins -> val s = ins.getInsets(WindowInsets.Type.systemBars()); topInset = s.top; botInset = s.bottom; applyInsets(); ins }
        content = FrameLayout(this); root.addView(content, FrameLayout.LayoutParams(-1, -1))
        pager = Pager(this); content.addView(pager, FrameLayout.LayoutParams(-1, -1))
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER }
        content.addView(dots, FrameLayout.LayoutParams(-1, T.dp(18), Gravity.BOTTOM))
        dock = LinearLayout(this).apply { gravity = Gravity.CENTER; background = T.round(T.card, 30); T.elevate(this, 4, 30); padDp(14, 10, 14, 10) }
        content.addView(dock, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        editBar = LinearLayout(this).apply { gravity = Gravity.CENTER; background = T.round(T.cardSolid, 24); T.elevate(this, 6, 24); padDp(10, 8, 10, 8); visibility = View.GONE }
        content.addView(editBar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        pager.onPos = { p -> drawDots(p); parallax(p) }
        pager.onSettled = { Store.put("home_page", it) }
        dots.setOnLongClickListener { toggleEdit(); true }
        Store.listeners.add(listener)
        try { val f = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package") }; if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED) else registerReceiver(pkgReceiver, f) } catch (e: Throwable) { Log.e("pkgrecv", e) }
        rebuild(Store.i("home_page", 0))
        Apps.load(this) { buildDock(); drawer?.refresh() }
        Sched.hourly(this); Water.arm(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        if (Store.b("timer_running")) try { TimerService.start(this) } catch (e: Exception) { }
        if (!Store.b("wp_applied") && Wallpaper.enabled()) Thread { if (Wallpaper.apply(this, true)) Store.put("wp_applied", true) }.start()
        if (!Store.b("welcomed")) { Store.put("welcomed", true); ui.postDelayed({ Dlg.info(this, "Welcome to Mission 2027", "Swipe between Today, Progress and Study. Swipe up on the dock or tap the search icon for all apps. Tap the sliders icon to rearrange or add widgets.\n\nOpen Settings > Zite and paste your API key to sync live data every hour. Pins are managed only from Settings > Apps.") ; openSettings("setup") }, 700) }
    }

    override fun onDestroy() { super.onDestroy(); Store.listeners.remove(listener); try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }; ui.removeCallbacks(refreshRun) }
    override fun onStart() { super.onStart(); try { widgetHost.startListening() } catch (e: Exception) { } }
    override fun onStop() { super.onStop(); try { widgetHost.stopListening() } catch (e: Exception) { }; Store.flushNow() }

    override fun onResume() {
        super.onResume()
        val was = T.dark; T.init(applicationContext)
        if (was != T.dark) { systemBars(); rebuild() }
        if (Zite.hasKey() && Zite.stale() && this.online()) Thread { Zite.sync(this); runOnUiThread { scheduleRefresh() } }.start()
        if (Focus.active()) Focus.finishIfDue(this)
        if (Store.b("pin_home") && Pin.isSet() && !Pin.unlocked()) gate { }
        scheduleRefresh(150)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        if (i.action == Intent.ACTION_MAIN && i.hasCategory(Intent.CATEGORY_HOME)) { closeDrawer(); if (i.getBooleanExtra("edit", false)) { i.removeExtra("edit"); if (!editing) toggleEdit() } else { if (editing) toggleEdit(); pager.goTo(0) } }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { if (drawer != null) closeDrawer() else if (editing) toggleEdit() else pager.goTo(0) }

    // ---------- layout ----------
    private fun applyInsets() {
        (dots.layoutParams as FrameLayout.LayoutParams).bottomMargin = botInset + T.dp(96)
        (dock.layoutParams as FrameLayout.LayoutParams).bottomMargin = botInset + T.dp(14)
        (editBar.layoutParams as FrameLayout.LayoutParams).bottomMargin = botInset + T.dp(14)
        for (i in 0 until pager.childCount) (pager.getChildAt(i) as? ScrollView)?.setPadding(0, topInset, 0, 0)
        dots.requestLayout(); dock.requestLayout()
    }
    private fun scheduleRefresh(delay: Long = 450) { ui.removeCallbacks(refreshRun); ui.postDelayed(refreshRun, delay) }
    override fun refresh() = scheduleRefresh(50)

    private fun rebuild(keepPage: Int = pager.index) {
        for (i in 0 until pager.childCount) (pager.getChildAt(i) as? ScrollView)?.let { scrollY[i] = it.scrollY }
        layout = HomeLayout.load()
        val pages = (0 until layout.length()).map { buildPage(it) }
        pager.setPages(pages, keepPage)
        pages.forEachIndexed { i, v -> (v as ScrollView).post { v.scrollTo(0, scrollY[i] ?: 0) } }
        applyInsets(); drawDots(pager.pos); buildDock(); updateEditBar()
    }

    private fun buildPage(i: Int): View {
        val p = layout.getJSONObject(i)
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; clipToPadding = false; setPadding(0, topInset, 0, 0) }
        attachGestures(sv)
        val col = vbox(); val wide = resources.displayMetrics.widthPixels / resources.displayMetrics.density > 700
        col.setPadding(T.dp(if (wide) 28 else 16), T.dp(10), T.dp(if (wide) 28 else 16), T.dp(150) + botInset)
        val ids = HomeLayout.cards(p)
        if (editing) col.addView(text(p.optString("name") + "  (page ${i + 1}/${layout.length()})", 13f, 3, T.text2).apply { padDp(6, 6, 6, 6) })
        else if (i > 0 && ids.firstOrNull() != "header") col.addView(text(p.optString("name"), 28f, 3).apply { padDp(6, 14, 6, 4); setShadowLayer(8f, 0f, 2f, T.withAlpha(T.page, 0.8f)) })
        if (wide && !editing) {
            // two-column masonry on tablets (Xiaomi Pad): header/countdown full width, the rest alternate
            val full = ids.filter { Cards.catalog[it]?.wide == true || it.startsWith("w:") }; val rest = ids.filter { it !in full }
            full.filter { it == "header" }.forEach { addCard(col, i, it, ids) }
            full.filter { it != "header" }.forEach { addCard(col, i, it, ids) }
            val l = vbox(); val r = vbox(); rest.forEachIndexed { k, id -> addCard(if (k % 2 == 0) l else r, i, id, ids) }
            val two = hbox(false); two.addView(l, LinearLayout.LayoutParams(0, -2, 1f)); two.addView(hspace(14)); two.addView(r, LinearLayout.LayoutParams(0, -2, 1f)); two.gravity = Gravity.TOP
            col.addView(two)
        } else ids.forEach { addCard(col, i, it, ids) }
        if (editing && ids.isEmpty()) col.addView(emptyNote("Empty page. Use Add widget below."))
        sv.addView(col, FrameLayout.LayoutParams(-1, -2))
        return sv
    }

    private fun addCard(col: LinearLayout, page: Int, id: String, ids: List<String>) {
        val v = cardView(id) ?: return
        val wrap = vbox()
        if (editing) {
            val idx = ids.indexOf(id); val title = if (id.startsWith("w:")) "System widget" else Cards.catalog[id]?.title ?: id
            val bar = hbox().apply { background = T.round(T.inset, 14); padDp(12, 4, 4, 4) }
            bar.addView(text(title, 12.5f, 2, T.text2), LinearLayout.LayoutParams(0, -2, 1f))
            bar.addView(iconBtn("up", T.text2, 34) { moveCard(page, idx, -1) }); bar.addView(iconBtn("down", T.text2, 34) { moveCard(page, idx, 1) })
            bar.addView(iconBtn("chev", T.text2, 34) { moveToPage(page, idx) })
            bar.addView(iconBtn("trash", T.danger, 34) { removeCard(page, idx) })
            wrap.addView(bar, lpm(-1, -2, 0, 0, 0, 6)); v.alpha = 0.96f
        }
        wrap.addView(v, LinearLayout.LayoutParams(-1, -2)); col.addView(wrap, lpm(-1, -2, 0, 0, 0, 14))
    }

    private fun attachGestures(sv: ScrollView) {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean { if (!editing) Gestures.run(this@MainActivity, Store.s("g_dtap", "lock")); return true }
            override fun onLongPress(e: MotionEvent) { if (!editing) { sv.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); Gestures.run(this@MainActivity, Store.s("g_long", "edit")) } }
        })
        sv.setOnTouchListener { _, e -> gd.onTouchEvent(e); false }
    }
    private var dx0 = 0f; private var dy0 = 0f; private var topAtDown = false
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (drawer == null && gateView == null && !editing) when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dx0 = e.rawX; dy0 = e.rawY; topAtDown = (pager.getChildAt(pager.index) as? ScrollView)?.scrollY == 0 }
            MotionEvent.ACTION_UP -> { val dy = e.rawY - dy0; val dx = e.rawX - dx0; if (topAtDown && dy > T.dp(130) && dy > Math.abs(dx) * 1.8f && e.rawY - dy0 < resources.displayMetrics.heightPixels) { topAtDown = false; Gestures.run(this, Store.s("g_down", "notif")) } }
        }
        return super.dispatchTouchEvent(e)
    }
    private fun cardView(id: String): View? {
        if (id.startsWith("w:")) return widgetView(id.substring(2).toIntOrNull() ?: return null)
        return Cards.make(this, id)
    }

    private fun widgetView(wid: Int): View? = try {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(wid) ?: return null
        val hv = widgetHost.createView(this, wid, info); hv.setAppWidget(wid, info)
        val h = maxOf(T.dp(110), (info.minHeight * resources.displayMetrics.density).toInt())
        FrameLayout(this).apply { background = T.round(T.card, 24); T.elevate(this, 3, 24); clipToOutline = true; addView(hv, FrameLayout.LayoutParams(-1, h)) }
    } catch (e: Throwable) { Log.e("widget", e); null }

    // ---------- editing ----------
    override fun toggleEdit() { editing = !editing; dock.visibility = if (editing) View.GONE else View.VISIBLE; editBar.visibility = if (editing) View.VISIBLE else View.GONE; rebuild() }
    private fun updateEditBar() {
        editBar.removeAllViews()
        if (!editing) return
        editBar.addView(pill("Add widget", "plus", true) { addWidgetDialog() }, lpm(-2, -2, 0, 0, 6, 0))
        editBar.addView(pill("Page", "layers", false) { pageMenu() }, lpm(-2, -2, 0, 0, 6, 0))
        editBar.addView(pill("Done", "check", false, T.sage) { toggleEdit() })
    }
    private fun mutate(f: (JSONArray) -> Unit) { val a = HomeLayout.load(); f(a); HomeLayout.save(a); rebuild() }
    private fun moveCard(page: Int, idx: Int, d: Int) = mutate { a -> val p = a.getJSONObject(page); val l = HomeLayout.cards(p); val j = idx + d; if (j in l.indices) { val t = l[idx]; l[idx] = l[j]; l[j] = t; HomeLayout.setCards(p, l) } }
    private fun removeCard(page: Int, idx: Int) = mutate { a -> val p = a.getJSONObject(page); val l = HomeLayout.cards(p); val id = l.removeAt(idx); HomeLayout.setCards(p, l); if (id.startsWith("w:")) try { widgetHost.deleteAppWidgetId(id.substring(2).toInt()) } catch (e: Exception) { } }
    private fun moveToPage(page: Int, idx: Int) {
        val a = HomeLayout.load(); val names = (0 until a.length()).filter { it != page }.map { it to a.getJSONObject(it).optString("name") }
        Dlg.choose(this, "Move to page", names.map { it.second }) { k -> mutate { arr -> val src = arr.getJSONObject(page); val l = HomeLayout.cards(src); val id = l.removeAt(idx); HomeLayout.setCards(src, l); val dst = arr.getJSONObject(names[k].first); val dl = HomeLayout.cards(dst); dl.add(id); HomeLayout.setCards(dst, dl) } }
    }
    private fun addWidgetDialog() {
        val a = HomeLayout.load(); val pi = pager.index.coerceIn(0, a.length() - 1); val have = HomeLayout.cards(a.getJSONObject(pi)).toSet()
        val avail = Cards.catalog.filter { it.key !in have }.toList()
        val items = avail.map { it.second.title } + listOf("Android widget (clock, calendar...)")
        Dlg.choose(this, "Add to this page", items) { i -> if (i == avail.size) pickSystemWidget() else mutate { arr -> val p = arr.getJSONObject(pi); val l = HomeLayout.cards(p); l.add(avail[i].first); HomeLayout.setCards(p, l) } }
    }
    private fun pageMenu() {
        Dlg.choose(this, "Page", listOf("Add new page", "Rename this page", "Delete this page", "Move page left", "Move page right", "Reset layout to default")) { i ->
            val pi = pager.index
            when (i) {
                0 -> Dlg.input(this, "New page name", "e.g. Revision", "", false, false, "Add") { n -> mutate { a -> a.put(JSONObject().put("name", n.ifBlank { "Page ${a.length() + 1}" }).put("cards", JSONArray())) }; pager.goTo(HomeLayout.load().length() - 1) }
                1 -> Dlg.input(this, "Rename page", "", HomeLayout.load().getJSONObject(pi).optString("name")) { n -> if (n.isNotBlank()) mutate { it.getJSONObject(pi).put("name", n) } }
                2 -> if (HomeLayout.load().length() <= 1) toast("Keep at least one page") else Dlg.confirm(this, "Delete page?", "Its cards are removed from home (your data stays).", "Delete") { mutate { a -> val l = (0 until a.length()).map { a.getJSONObject(it) }.toMutableList(); l.removeAt(pi); HomeLayout.save(JSONArray(l)); a.let { } } ; rebuild(maxOf(0, pi - 1)) }
                3, 4 -> { val d = if (i == 3) -1 else 1; val a = HomeLayout.load(); val j = pi + d; if (j in 0 until a.length()) { val l = (0 until a.length()).map { a.getJSONObject(it) }.toMutableList(); val t = l[pi]; l[pi] = l[j]; l[j] = t; HomeLayout.save(JSONArray(l)); rebuild(j) } }
                5 -> Dlg.confirm(this, "Reset layout?", "Restores the 3 default pages. System widgets are removed.", "Reset") { HomeLayout.reset(); rebuild(0) }
            }
        }
    }

    // ---------- system widgets ----------
    override fun pickSystemWidget() {
        try {
            pendingWidget = widgetHost.allocateAppWidgetId()
            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget), REQ_PICK)
        } catch (e: Exception) { toast("Widgets are not available on this device") }
    }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, d: Intent?) {
        super.onActivityResult(req, res, d)
        when (req) {
            REQ_PICK -> { val id = d?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget) ?: pendingWidget; if (res != RESULT_OK) { dropWidget(id); return }; bindWidget(id) }
            REQ_BIND -> if (res == RESULT_OK) configureWidget(pendingWidget) else dropWidget(pendingWidget)
            REQ_CONF -> if (res == RESULT_OK) placeWidget(pendingWidget) else dropWidget(pendingWidget)
        }
    }
    private fun dropWidget(id: Int) { try { widgetHost.deleteAppWidgetId(id) } catch (e: Exception) { } }
    private fun bindWidget(id: Int) {
        pendingWidget = id; val awm = AppWidgetManager.getInstance(this); val info = awm.getAppWidgetInfo(id)
        if (info != null && awm.bindAppWidgetIdIfAllowed(id, info.provider)) configureWidget(id)
        else if (info != null) startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider), REQ_BIND)
        else dropWidget(id)
    }
    private fun configureWidget(id: Int) {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (info?.configure != null) try { startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), REQ_CONF) } catch (e: Exception) { placeWidget(id) } else placeWidget(id)
    }
    private fun placeWidget(id: Int) { mutate { a -> val p = a.getJSONObject(pager.index.coerceIn(0, a.length() - 1)); val l = HomeLayout.cards(p); l.add("w:$id"); HomeLayout.setCards(p, l) } }

    // ---------- dock / drawer ----------
    private fun buildDock() {
        dock.removeAllViews(); val pins = Apps.pins().mapNotNull { Apps.byPkg(it) }.take(5)
        pins.forEach { e -> dock.addView(ImageView(this).apply { setImageDrawable(e.icon.constantState?.newDrawable()?.mutate() ?: e.icon); setOnClickListener { Apps.launch(this@MainActivity, e.pkg) }
            setOnLongClickListener { Dlg.choose(this@MainActivity, e.label, listOf("Open in floating window", "Open in split / adjacent", "App info")) { i -> Apps.launch(this@MainActivity, e.pkg, if (i == 0) "float" else if (i == 1) "split" else "normal"); if (i == 2) Apps.info(this@MainActivity, e.pkg) }; true } }, lpm(T.dp(52), T.dp(52), 7, 0, 7, 0)) }
        if (pins.isEmpty()) dock.addView(text("Pin apps in Settings > Apps", 12f, 1, T.text3).apply { padDp(10, 0, 10, 0) })
        dock.addView(FrameLayout(this).apply { background = T.round(T.inset, 16); addView(icon("grid", T.text, 24, 1.8f), FrameLayout.LayoutParams(T.dp(24), T.dp(24), Gravity.CENTER)); tap { openDrawer() } }, lpm(T.dp(52), T.dp(52), 7, 0, 7, 0))
        var y0 = 0f
        dock.setOnTouchListener { _, e -> when (e.actionMasked) { MotionEvent.ACTION_DOWN -> y0 = e.rawY; MotionEvent.ACTION_UP -> if (y0 - e.rawY > T.dp(40)) Gestures.run(this, Store.s("g_up", "drawer")) }; false }
    }
    override fun openDrawer() {
        if (drawer != null) return
        val d = DrawerView(this) { closeDrawer() }
        d.setPadding(0, topInset, 0, botInset); drawer = d; root.addView(d, FrameLayout.LayoutParams(-1, -1))
        d.translationY = resources.displayMetrics.heightPixels.toFloat(); d.animate().translationY(0f).setDuration(240).start()
        var y0 = 0f
    }
    private fun closeDrawer() {
        val d = drawer ?: return; drawer = null
        d.animate().translationY(resources.displayMetrics.heightPixels.toFloat()).setDuration(200).withEndAction { root.removeView(d) }.start()
    }

    // ---------- PIN gate ----------
    fun gate(then: () -> Unit) {
        if (!Pin.isSet() || Pin.unlocked()) { then(); return }
        if (gateView != null) return
        val pad = PinPad(this, "Enter PIN", Pin.len(), !Store.b("pin_home"), { code, p ->
            if (Pin.lockLeft() > 0) p.error("Locked: wait ${Pin.lockLeft()}s")
            else if (Pin.check(code)) { root.removeView(p); gateView = null; then() } else p.error(if (Pin.lockLeft() > 0) "Too many attempts: ${Pin.lockLeft()}s" else "Wrong PIN")
        }, { root.removeView(gateView); gateView = null },
            if (Bio.available(this) && Store.b("pin_bio", true)) ({ Bio.prompt(this) { runOnUiThread { gateView?.let { root.removeView(it) }; gateView = null; then() } } }) else null)
        gateView = pad; root.addView(pad, FrameLayout.LayoutParams(-1, -1))
    }

    // ---------- Host ----------
    override fun openChat(prefill: String?) { startActivity(Intent(this, ChatActivity::class.java).apply { if (prefill != null) putExtra("q", prefill) }) }
    override fun openSettings(path: String?) { gate { startActivity(Intent(this, SettingsActivity::class.java).apply { if (path != null) putExtra("path", path) }) } }

    private fun drawDots(p: Float) {
        val n = pager.count; if (dots.childCount != n) { dots.removeAllViews(); for (i in 0 until n) dots.addView(View(this), lpm(T.dp(8), T.dp(8), 4, 0, 4, 0)) }
        for (i in 0 until n) { val v = dots.getChildAt(i); val a = (1f - Math.abs(i - p)).coerceIn(0f, 1f); v.background = T.round(T.withAlpha(T.text, 0.25f + 0.65f * a), 4); val lp = v.layoutParams; lp.width = T.dp(8) + (T.dp(10) * a).toInt(); v.layoutParams = lp }
        dots.visibility = if (n > 1) View.VISIBLE else View.GONE
    }
    private fun parallax(p: Float) { if (!Store.b("wp_parallax", true) || pager.count < 2) return; try { WallpaperManager.getInstance(this).setWallpaperOffsets(root.windowToken, p / (pager.count - 1), 0.5f) } catch (e: Exception) { } }
}
