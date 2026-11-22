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
        .put(page("Today"))
        .put(page("Progress"))
        .put(page("Study"))
    private fun page(name: String, vararg ids: String) = JSONObject().put("name", name).put("cards", JSONArray(ids.toList()))
    fun load(): JSONArray = try {
        val saved = Store.s(KEY)
        if (saved.isBlank()) defaults() else JSONArray(saved).also { pages ->
            if (pages.length() == 0) return defaults()
            for (i in 0 until pages.length()) {
                val page = pages.optJSONObject(i) ?: continue
                setCards(page, cards(page).filterNot { it == "mains" })
            }
        }
    } catch (e: Exception) { defaults() }
    fun save(a: JSONArray) { Store.put(KEY, a.toString()) }
    fun reset() {
        val current = load()
        val widgetsByPage = (0 until current.length()).associateWith { index ->
            cards(current.getJSONObject(index)).filter { it.startsWith("w:") }
        }
        val reset = defaults()
        widgetsByPage.forEach { (index, widgets) ->
            if (widgets.isNotEmpty()) {
                val destination = minOf(index, reset.length() - 1)
                val page = reset.getJSONObject(destination)
                setCards(page, cards(page) + widgets)
            }
        }
        save(reset)
    }
    fun widgetIds(): List<Int> = load().let { pages ->
        (0 until pages.length()).flatMap { cards(pages.getJSONObject(it)) }
            .filter { it.startsWith("w:") }
            .mapNotNull { it.removePrefix("w:").toIntOrNull() }
    }
    fun removeWidget(id: Int): Boolean {
        val pages = load()
        var removed = false
        for (index in 0 until pages.length()) {
            val page = pages.getJSONObject(index)
            val cards = cards(page)
            if (cards.remove("w:$id")) {
                setCards(page, cards)
                removed = true
            }
        }
        if (removed) save(pages)
        return removed
    }
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
    private var edgeHandle: View? = null
    private val edgeHide = Runnable {
        edgeHandle?.let { h ->
            h.animate().cancel()
            h.animate().alpha(0f).setDuration(180).withEndAction {
                if (edgeHandle === h) {
                    val lp = h.layoutParams as FrameLayout.LayoutParams
                    lp.width = T.dp(8)
                    h.layoutParams = lp
                    h.visibility = View.VISIBLE
                }
            }.start()
        }
    }
    private var topInset = 0; private var botInset = 0
    private val scrollY = HashMap<Int, Int>()
    private lateinit var widgetHost: AppWidgetHost
    private val REQ_PICK = 71; private val REQ_BIND = 72; private val REQ_CONF = 73; private val REQ_NOTIF = 74
    private val STATE_PENDING_WIDGET = "pending_app_widget_id"
    private var pendingWidget = -1
    private var layout = JSONArray()
    private val listener: () -> Unit = { scheduleRefresh(); updateEdgeHandle() }
    private val refreshRun = Runnable { if (pager.busy) scheduleRefresh(600) else if (currentFocus !is EditText) rebuild() else scheduleRefresh(2500) }

    private val pkgReceiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { Apps.load(this@MainActivity) { drawer?.refresh(); buildDock() } } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val diagnostics = getSharedPreferences("mission_diagnostics", MODE_PRIVATE)
        if (diagnostics.getBoolean("safe_mode", false)) {
            Store.put("safe_mode", true)
            toast("Safe mode: repeated crashes detected. Diagnostics are available in Settings.")
        } else {
            Store.put("safe_mode", false)
        }
        pendingWidget = b?.getInt(STATE_PENDING_WIDGET, -1) ?: -1
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
        updateEdgeHandle()
        editBar = LinearLayout(this).apply { gravity = Gravity.CENTER; background = T.round(T.cardSolid, 24); T.elevate(this, 6, 24); padDp(10, 8, 10, 8); visibility = View.GONE }
        content.addView(editBar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        pager.onPos = { p -> drawDots(p); parallax(p) }
        pager.onSettled = { Store.put("home_page", it) }
        dots.setOnLongClickListener { toggleEdit(); true }
        Store.listeners.add(listener)
        try { val f = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package") }; if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED) else registerReceiver(pkgReceiver, f) } catch (e: Throwable) { Log.e("pkgrecv", e) }
        rebuild(Store.i("home_page", 0))
        Apps.load(this) { buildDock(); drawer?.refresh() }
        Sched.hourly(this); Water.arm(this); Wallpaper.scheduleNext(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        if (TimerState.active()) TimerService.start(this)
        if (!Store.b("wp_applied") && Wallpaper.enabled()) Thread { if (Wallpaper.apply(this, true)) Store.put("wp_applied", true) }.start()
        if (!Store.b("welcomed")) { Store.put("welcomed", true); ui.postDelayed({ Dlg.info(this, "Welcome to Mission 2027", "Swipe between Today, Progress and Study. Swipe up on the dock or tap the search icon for all apps. Tap the sliders icon to rearrange or add widgets.\n\nSet your reset password first. Then open Settings > Zite to configure live sync.") ; openSettings("reset") }, 700) }
    }

    override fun onDestroy() { super.onDestroy(); Store.listeners.remove(listener); try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }; ui.removeCallbacks(refreshRun); ui.removeCallbacks(edgeHide) }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PENDING_WIDGET, pendingWidget)
        super.onSaveInstanceState(outState)
    }
    override fun onStart() { super.onStart(); try { widgetHost.startListening() } catch (e: Exception) { } }
    override fun onStop() { super.onStop(); try { widgetHost.stopListening() } catch (e: Exception) { }; Store.flushNow() }

    override fun onResume() {
        super.onResume()
        val was = T.dark; T.init(applicationContext)
        if (was != T.dark) { systemBars(); rebuild() }
        updateEdgeHandle()
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
    private fun scheduleRefresh(delay: Long = 300) { ui.removeCallbacks(refreshRun); ui.postDelayed(refreshRun, delay) }
    override fun refresh() = scheduleRefresh(50)

    private fun rebuild(keepPage: Int = pager.index) {
        val safeMode = Store.b("safe_mode", false)
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
        val ids = if (Store.b("safe_mode", false)) emptyList() else HomeLayout.cards(p)
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
        if (ids.isEmpty()) col.addView(emptyNote(if (editing) "Empty page. Use Add widget below." else "No widgets added yet. Tap the sliders icon to choose your cards."))
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
            if (id.startsWith("w:")) {
                val wid = id.substringAfter("w:").toIntOrNull()
                val info = wid?.let { AppWidgetManager.getInstance(this).getAppWidgetInfo(it) }
                if (wid != null && info != null && (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
                    bar.addView(iconBtn("pencil", T.text2, 34) { resizeWidget(wid) })
                }
            } else bar.addView(iconBtn("close", T.danger, 34) { removeCard(page, idx) })
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
        if (id.startsWith("w:")) {
            val wid = id.substring(2).toIntOrNull()
            return if (wid == null) widgetUnavailable("Invalid saved widget ID") else widgetView(wid)
        }
        return Cards.make(this, id)
    }

    private fun widgetView(wid: Int): View? = try {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(wid)
            ?: return widgetUnavailable("Its provider is unavailable. Reinstall or enable the widget app, or remove this placement in Settings.")
        val hv = widgetHost.createView(this, wid, info); hv.setAppWidget(wid, info)
        val defaultDp = maxOf(110, widgetPxToDp(info.minHeight)).coerceIn(100, 600)
        val heightDp = if ((info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
            val bounds = widgetHeightBounds(info)
            Store.i("widget_height_$wid", defaultDp).coerceIn(bounds.first, bounds.last)
        } else defaultDp
        FrameLayout(this).apply { background = T.round(T.card, 24); T.elevate(this, 3, 24); clipToOutline = true; addView(hv, FrameLayout.LayoutParams(-1, T.dpFixed(heightDp))) }
    } catch (e: Exception) {
        Log.e("widget", e)
        widgetUnavailable("Could not load this Android widget: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun widgetUnavailable(message: String): View = Card(this, 22).apply {
        addView(this@MainActivity.cardHeader("grid", "Android widget unavailable", T.slate))
        addView(this@MainActivity.text(message, 12.5f, 0, T.text2), lp(-2, -2, 8))
        addView(this@MainActivity.text("Its saved home placement is kept. Remove it only from Settings > Reset and data > Remove Android widget.", 11.5f, 0, T.text3), lp(-2, -2, 4))
    }

    private fun resizeWidget(wid: Int) {
        val manager = AppWidgetManager.getInstance(this)
        val info = manager.getAppWidgetInfo(wid)
        if (info == null) { toast("The widget provider is unavailable"); return }
        if ((info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) == 0) {
            toast("This widget does not support vertical resizing")
            return
        }
        val bounds = widgetHeightBounds(info)
        val minDp = bounds.first
        val maxDp = bounds.last
        val initial = Store.i("widget_height_$wid", maxOf(minDp, widgetPxToDp(info.minHeight))).coerceIn(minDp, maxDp)
        Dlg.input(this, "Android widget height", "$minDp-$maxDp dp", initial.toString(), true) { value ->
            val height = value.toIntOrNull()
            if (height == null || height !in minDp..maxDp) {
                toast("Enter a height from $minDp to $maxDp dp")
                return@input
            }
            try {
                val options = manager.getAppWidgetOptions(wid)
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                manager.updateAppWidgetOptions(wid, options)
            } catch (e: Exception) {
                Log.e("widget-resize", e)
                toast("Could not resize this widget")
                return@input
            }
            Store.put("widget_height_$wid", height)
            rebuild()
        }
    }
    private fun widgetPxToDp(px: Int): Int = Math.round(px / resources.displayMetrics.density)
    private fun widgetHeightBounds(info: AppWidgetProviderInfo): IntRange {
        val minPx = if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
        val minDp = widgetPxToDp(minPx).coerceIn(100, 600)
        val maxDp = if (Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) {
            widgetPxToDp(info.maxResizeHeight).coerceIn(minDp, 600)
        } else {
            600
        }
        return minDp..maxDp
    }

    // ---------- editing ----------
    override fun toggleEdit() {
        editing = !editing
        dock.visibility = if (editing) View.GONE else View.VISIBLE
        editBar.visibility = if (editing) View.VISIBLE else View.GONE
        if (editing) {
            ui.removeCallbacks(edgeHide)
            edgeHandle?.let { it.animate().cancel(); it.visibility = View.GONE }
        } else showEdgeHandle()
        rebuild()
    }
    private fun updateEditBar() {
        editBar.removeAllViews()
        if (!editing) return
        editBar.addView(pill("Add widget", "plus", true) { addWidgetDialog() }, lpm(-2, -2, 0, 0, 6, 0))
        editBar.addView(pill("Page", "layers", false) { pageMenu() }, lpm(-2, -2, 0, 0, 6, 0))
        editBar.addView(pill("Done", "check", false, T.sage) { toggleEdit() })
    }
    private fun mutate(f: (JSONArray) -> Unit) { val a = HomeLayout.load(); f(a); HomeLayout.save(a); rebuild() }
    private fun moveCard(page: Int, idx: Int, d: Int) = mutate { a -> val p = a.getJSONObject(page); val l = HomeLayout.cards(p); val j = idx + d; if (j in l.indices) { val t = l[idx]; l[idx] = l[j]; l[j] = t; HomeLayout.setCards(p, l) } }
    private fun removeCard(page: Int, idx: Int) {
        val pages = HomeLayout.load()
        val p = pages.optJSONObject(page) ?: return
        val id = HomeLayout.cards(p).getOrNull(idx) ?: return
        Dlg.confirm(this, "Remove ${Cards.catalog[id]?.title ?: id} from home?", "Only its home-screen placement is removed. Your saved study data stays intact.", "Remove") {
            mutate { current ->
                val target = current.optJSONObject(page) ?: return@mutate
                val ids = HomeLayout.cards(target)
                if (ids.getOrNull(idx) == id) { ids.removeAt(idx); HomeLayout.setCards(target, ids) }
            }
        }
    }
    private fun moveToPage(page: Int, idx: Int) {
        val a = HomeLayout.load(); val names = (0 until a.length()).filter { it != page }.map { it to a.getJSONObject(it).optString("name") }
        Dlg.choose(this, "Move to page", names.map { it.second }) { k -> mutate { arr -> val src = arr.getJSONObject(page); val l = HomeLayout.cards(src); val id = l.removeAt(idx); HomeLayout.setCards(src, l); val dst = arr.getJSONObject(names[k].first); val dl = HomeLayout.cards(dst); dl.add(id); HomeLayout.setCards(dst, dl) } }
    }
    private fun addWidgetDialog() {
        val pages = HomeLayout.load()
        val pageIndex = pager.index.coerceIn(0, pages.length() - 1)
        val placed = HomeLayout.cards(pages.getJSONObject(pageIndex)).toSet()
        val available = Cards.catalog.filter { it.key !in placed }
        val systemWidgetOption = "system:android-widget"
        val options = available.map { (id, meta) ->
            WidgetCatalogDialog.Option(
                id,
                meta.title,
                if (meta.wide) "Mission widget · full width" else "Mission widget"
            )
        } + WidgetCatalogDialog.Option(
            systemWidgetOption,
            "Android app widgets",
            "Widgets provided by apps installed on this device"
        )
        WidgetCatalogDialog.show(this, options) { option ->
            if (option.id == systemWidgetOption) {
                pickSystemWidget()
            } else {
                mutate { current ->
                    val page = current.optJSONObject(pageIndex) ?: return@mutate
                    val cards = HomeLayout.cards(page)
                    if (option.id in Cards.catalog && option.id !in cards) {
                        cards.add(option.id)
                        HomeLayout.setCards(page, cards)
                    }
                }
            }
        }
    }
    private fun pageMenu() {
        Dlg.choose(this, "Page", listOf("Add new page", "Rename this page", "Move page left", "Move page right")) { i ->
            val pi = pager.index
            when (i) {
                0 -> Dlg.input(this, "New page name", "e.g. Revision", "", false, false, "Add") { n -> mutate { a -> a.put(JSONObject().put("name", n.ifBlank { "Page ${a.length() + 1}" }).put("cards", JSONArray())) }; pager.goTo(HomeLayout.load().length() - 1) }
                1 -> Dlg.input(this, "Rename page", "", HomeLayout.load().getJSONObject(pi).optString("name")) { n -> if (n.isNotBlank()) mutate { it.getJSONObject(pi).put("name", n) } }
                2, 3 -> { val d = if (i == 2) -1 else 1; val a = HomeLayout.load(); val j = pi + d; if (j in 0 until a.length()) { val l = (0 until a.length()).map { a.getJSONObject(it) }.toMutableList(); val t = l[pi]; l[pi] = l[j]; l[j] = t; HomeLayout.save(JSONArray(l)); rebuild(j) } }
            }
        }
    }

    // ---------- system widgets ----------
    override fun pickSystemWidget() {
        var allocated = -1
        try {
            allocated = widgetHost.allocateAppWidgetId()
            pendingWidget = allocated
            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget), REQ_PICK)
        } catch (e: Exception) {
            Log.e("widget-pick", e)
            dropWidget(allocated)
            toast("Could not open the Android widget picker")
        }
    }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, d: Intent?) {
        super.onActivityResult(req, res, d)
        when (req) {
            REQ_PICK -> {
                val id = d?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget) ?: pendingWidget
                if (res != RESULT_OK) {
                    if (id >= 0) dropWidget(id)
                    if (pendingWidget >= 0 && pendingWidget != id) dropWidget(pendingWidget)
                    return
                }
                if (id < 0) {
                    if (pendingWidget >= 0) dropWidget(pendingWidget)
                    Log.e("widget-pick", IllegalStateException("Picker returned no widget ID"))
                    toast("The widget picker returned no widget ID")
                    return
                }
                if (pendingWidget >= 0 && id != pendingWidget) dropWidget(pendingWidget)
                bindWidget(id)
            }
            REQ_BIND -> if (res == RESULT_OK) configureWidget(pendingWidget) else dropWidget(pendingWidget)
            REQ_CONF -> if (res == RESULT_OK) placeWidget(pendingWidget) else dropWidget(pendingWidget)
        }
    }
    private fun dropWidget(id: Int) {
        if (id < 0) return
        try { widgetHost.deleteAppWidgetId(id) } catch (e: Exception) { Log.e("widget-id-release", e) }
        if (pendingWidget == id) pendingWidget = -1
    }
    private fun bindWidget(id: Int) {
        pendingWidget = id
        val awm = AppWidgetManager.getInstance(this)
        val info = awm.getAppWidgetInfo(id)
        if (info == null) {
            dropWidget(id); toast("The selected widget provider is no longer available")
            return
        }
        try {
            if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) configureWidget(id)
            else startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider),
                REQ_BIND
            )
        } catch (e: Exception) {
            Log.e("widget-bind", e); dropWidget(id); toast("Could not bind the Android widget")
        }
    }
    private fun configureWidget(id: Int) {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (info == null) {
            dropWidget(id); toast("The selected widget provider is no longer available")
            return
        }
        if (info.configure == null) {
            placeWidget(id)
            return
        }
        try {
            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), REQ_CONF)
        } catch (e: Exception) {
            Log.e("widget-configure", e); toast("Widget setup could not open; adding its default layout"); placeWidget(id)
        }
    }
    private fun placeWidget(id: Int) {
        if (id < 0) { toast("Cannot add a widget without a valid ID"); return }
        mutate { a -> val p = a.getJSONObject(pager.index.coerceIn(0, a.length() - 1)); val l = HomeLayout.cards(p); if ("w:$id" !in l) l.add("w:$id"); HomeLayout.setCards(p, l) }
        if (pendingWidget == id) pendingWidget = -1
    }

    // ---------- dock / drawer ----------
    private fun buildDock() {
        dock.removeAllViews(); val pins = Apps.pins().mapNotNull { Apps.byPkg(it) }.take(5)
        val pinSize = T.dp(Store.i("dock_icon", 52).coerceIn(24, 72))
        pins.forEach { e -> dock.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageDrawable(e.icon.constantState?.newDrawable()?.mutate() ?: e.icon); setOnClickListener { Apps.launch(this@MainActivity, e.pkg) }
            setOnLongClickListener { Dlg.choose(this@MainActivity, e.label, listOf("Open in floating window", "Open in split / adjacent", "App info")) { i -> Apps.launch(this@MainActivity, e.pkg, if (i == 0) "float" else if (i == 1) "split" else "normal"); if (i == 2) Apps.info(this@MainActivity, e.pkg) }; true } }, lpm(pinSize, pinSize, 7, 0, 7, 0)) }
        if (pins.isEmpty()) dock.addView(text("Pin apps in Settings > Apps", 12f, 1, T.text3).apply { padDp(10, 0, 10, 0) })
        dock.addView(FrameLayout(this).apply { background = T.round(T.inset, 16); addView(icon("grid", T.text, 24, 1.8f), FrameLayout.LayoutParams(T.dp(24), T.dp(24), Gravity.CENTER)); tap { openDrawer() } }, lpm(T.dp(52), T.dp(52), 7, 0, 7, 0))
        var y0 = 0f
        dock.setOnTouchListener { _, e -> when (e.actionMasked) { MotionEvent.ACTION_DOWN -> y0 = e.rawY; MotionEvent.ACTION_UP -> if (y0 - e.rawY > T.dp(40)) Gestures.run(this, Store.s("g_up", "drawer")) }; false }
    }
    private fun updateEdgeHandle() {
        edgeHandle?.let { root.removeView(it) }
        edgeHandle = null
        ui.removeCallbacks(edgeHide)
        if (!Store.b("edge_handle", false)) return

        val handle = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Open app drawer"
            val bar = View(this@MainActivity).apply { background = T.round(T.withAlpha(T.text2, 0.72f), 10) }
            addView(bar, FrameLayout.LayoutParams(T.dp(8), T.dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) showEdgeHandle()
                false
            }
            setOnClickListener {
                ui.removeCallbacks(edgeHide)
                openDrawer()
            }
        }
        edgeHandle = handle
        root.addView(handle, FrameLayout.LayoutParams(T.dp(24), T.dp(52), Gravity.START or Gravity.CENTER_VERTICAL))
        showEdgeHandle()
    }
    private fun showEdgeHandle() {
        val h = edgeHandle ?: return
        if (drawer != null || gateView != null || editing) {
            ui.removeCallbacks(edgeHide)
            h.animate().cancel()
            h.alpha = 0f
            h.visibility = View.GONE
            return
        }
        h.animate().cancel()
        val lp = h.layoutParams as FrameLayout.LayoutParams
        lp.width = T.dp(24)
        h.layoutParams = lp
        h.alpha = 1f
        h.visibility = View.VISIBLE
        ui.removeCallbacks(edgeHide)
        ui.postDelayed(edgeHide, 3500)
    }
    private fun hideEdgeStrip(h: View) {
        h.animate().cancel()
        h.alpha = 0f
        val lp = h.layoutParams as FrameLayout.LayoutParams
        lp.width = T.dp(8)
        h.layoutParams = lp
        h.visibility = View.VISIBLE
    }
    override fun openDrawer() {
        if (drawer != null || gateView != null || editing) return
        ui.removeCallbacks(edgeHide)
        edgeHandle?.let { hideEdgeStrip(it) }
        val d = DrawerView(this) { closeDrawer() }
        d.setPadding(0, topInset, 0, botInset); drawer = d; root.addView(d, FrameLayout.LayoutParams(-1, -1))
        d.translationY = resources.displayMetrics.heightPixels.toFloat(); d.animate().translationY(0f).setDuration(240).start()
        var y0 = 0f
    }
    private fun closeDrawer() {
        val d = drawer ?: return; drawer = null
        d.animate().translationY(resources.displayMetrics.heightPixels.toFloat()).setDuration(200).withEndAction { root.removeView(d) }.start()
        ui.postDelayed({ showEdgeHandle() }, 220)
    }

    // ---------- PIN gate ----------
    fun gate(then: () -> Unit) {
        if (!Pin.isSet() || Pin.unlocked()) { then(); return }
        if (gateView != null) return
        ui.removeCallbacks(edgeHide)
        edgeHandle?.let { it.animate().cancel(); it.visibility = View.GONE }
        val pad = PinPad(this, "Enter PIN", Pin.len(), !Store.b("pin_home"), { code, p ->
            if (Pin.lockLeft() > 0) p.error("Locked: wait ${Pin.lockLeft()}s")
            else if (Pin.check(code)) { root.removeView(p); gateView = null; showEdgeHandle(); then() } else p.error(if (Pin.lockLeft() > 0) "Too many attempts: ${Pin.lockLeft()}s" else "Wrong PIN")
        }, { root.removeView(gateView); gateView = null; showEdgeHandle() },
            if (Bio.available(this) && Store.b("pin_bio", true)) ({ Bio.prompt(this) { runOnUiThread { gateView?.let { root.removeView(it) }; gateView = null; showEdgeHandle(); then() } } }) else null)
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
