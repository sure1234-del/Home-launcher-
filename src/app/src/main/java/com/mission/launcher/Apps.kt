package com.mission.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.time.LocalDate

class AppEntry(val label: String, val pkg: String, val cn: ComponentName, val icon: Drawable)

object Apps {
    @Volatile var list: List<AppEntry> = emptyList()
    var iconCacheClear: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun hidden(): Set<String> = Store.stringList("hidden_apps").toSet()

    fun study(): Set<String> = Store.stringList("study_apps").toSet()

    fun pins(): List<String> = Store.stringList("pins")

    fun toggleList(
        key: String,
        pkg: String,
    ) {
        val l = Store.stringList(key).toMutableList()
        if (!l.remove(pkg)) l.add(pkg)
        Store.setStringList(key, l)
        Store.changed()
    }

    fun byPkg(p: String) = list.firstOrNull { it.pkg == p }

    fun load(
        c: Context,
        done: () -> Unit,
    ) {
        Thread {
            try {
                val la = c.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val out =
                    la.getActivityList(null, Process.myUserHandle()).filter { it.applicationInfo.packageName != c.packageName }
                        .map {
                            AppEntry(
                                it.label.toString(),
                                it.applicationInfo.packageName,
                                it.componentName,
                                it.getIcon(0),
                            )
                        }.sortedBy { it.label.lowercase() }
                list = out
            } catch (e: Exception) {
                Log.e("apps", e)
            }
            main.post(done)
        }.start()
    }

    /** Packages usable while a focus session is running. */
    fun allowedInFocus(
        c: Context,
        pkg: String,
    ): Boolean {
        if (pkg in study()) return true
        val ess =
            setOf("com.android.settings", "com.miui.securitycenter", "com.android.deskclock", "com.google.android.deskclock", "com.android.calculator2", "com.miui.calculator", "com.mi.android.globalFileexplorer", "com.google.android.apps.docs", "com.google.android.apps.bard", "com.openai.chatgpt", "com.flexcil.flexcilnote")
        return pkg in ess
    }

    fun launch(
        a: Activity,
        pkg: String,
        mode: String = "normal",
    ) {
        if (Focus.active() && !allowedInFocus(a, pkg)) {
            a.toast("Focus session running - ${Focus.left() / 60} min left")
            return
        }
        Guard.check(a, pkg) { doLaunch(a, pkg, if (mode == "normal") Store.s("open_mode", "normal") else mode) }
    }

    private fun doLaunch(
        a: Activity,
        pkg: String,
        mode: String,
    ) {
        val e = byPkg(pkg)
        try {
            val cn = e?.cn ?: a.packageManager.getLaunchIntentForPackage(pkg)?.component ?: return
            if (mode == "normal") {
                try {
                    (
                        a.getSystemService(
                            Context.LAUNCHER_APPS_SERVICE,
                        ) as LauncherApps
                    ).startMainActivity(cn, Process.myUserHandle(), null, null)
                    return
                } catch (
                    x: Exception,
                ) {
                }
                a.startActivity(
                    Intent(
                        Intent.ACTION_MAIN,
                    ).addCategory(
                        Intent.CATEGORY_LAUNCHER,
                    ).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            } else {
                val i =
                    Intent(
                        Intent.ACTION_MAIN,
                    ).addCategory(
                        Intent.CATEGORY_LAUNCHER,
                    ).setComponent(
                        cn,
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                val o = ActivityOptions.makeBasic()
                if (mode == "float") {
                    val dm = a.resources.displayMetrics
                    val w = (dm.widthPixels * 0.58f).toInt()
                    val h = (dm.heightPixels * 0.72f).toInt()
                    val l = (dm.widthPixels - w) / 2
                    val t = (dm.heightPixels - h) / 3
                    o.setLaunchBounds(Rect(l, t, l + w, t + h))
                }
                a.startActivity(i, o.toBundle())
            }
        } catch (x: Exception) {
            a.toast("Cannot open this app")
        }
    }

    fun info(
        a: Activity,
        pkg: String,
    ) {
        try {
            a.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
        }
    }

    fun uninstall(
        a: Activity,
        pkg: String,
    ) {
        try {
            a.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        } catch (e: Exception) {
        }
    }
}

object Usage {
    fun granted(c: Context): Boolean {
        return try {
            val ao = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun foregroundPackage(c: Context): String? {
        if (!granted(c)) return null
        try {
            val now = System.currentTimeMillis()
            val manager = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = manager.queryEvents((now - 30 * 60 * 1000L).coerceAtLeast(0L), now)
            val event = android.app.usage.UsageEvents.Event()
            var latestAt = 0L
            var latestPackage: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if ((
                        event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                            event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
                    ) &&
                    event.timeStamp >= latestAt
                ) {
                    latestAt = event.timeStamp
                    latestPackage = event.packageName
                }
            }
            return latestPackage
        } catch (e: Exception) {
            Log.e("usage-foreground", e)
            return null
        }
    }

    fun today(c: Context): Map<String, Long> {
        return try {
            val um = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val start = Repo.startOf(LocalDate.now(Repo.zone()))
            um.queryAndAggregateUsageStats(start, System.currentTimeMillis()).mapValues {
                it.value.totalTimeInForeground
            }.filter { it.value > 30000 && it.key != c.packageName }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun openSettings(c: Context) {
        try {
            c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }
}

/** Full-screen app drawer: search (Enter opens the first hit), All/Study tabs, A-Z index, long-press actions. */
class DrawerView(val a: Activity, val onClose: () -> Unit) : FrameLayout(a) {
    private var tab = 0
    private var query = ""
    private var shown: List<AppEntry> = emptyList()
    private val grid = GridView(a)
    private val extra = a.vbox()
    private val search: EditText
    private val adapter =
        object : BaseAdapter() {
            override fun getCount() = shown.size

            override fun getItem(p: Int) = shown[p]

            override fun getItemId(p: Int) = p.toLong()

            override fun getView(
                p: Int,
                cv: View?,
                parent: ViewGroup?,
            ): View {
                val e = shown[p]
                val cell =
                    (cv as? LinearLayout) ?: a.vbox().apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        padDp(4, 10, 4, 6)
                        val icon = ImageView(a).apply { id = View.generateViewId() }
                        val label =
                            a.text("", 11.5f, 1, T.text, 1).apply {
                                id = View.generateViewId()
                                gravity = Gravity.CENTER
                            }
                        addView(icon, LinearLayout.LayoutParams(T.dp(Store.i("drawer_icon", 54)), T.dp(Store.i("drawer_icon", 54))))
                        addView(label, lp(-1, -2, 6))
                        tag = listOf(icon, label)
                    }
                val views = cell.tag as? List<*>
                (views?.getOrNull(0) as? ImageView)?.setImageDrawable(e.icon)
                (views?.getOrNull(1) as? TextView)?.apply {
                    text = e.label
                    visibility = if (Store.b("drawer_labels", true)) View.VISIBLE else View.GONE
                }
                return cell
            }
        }

    init {
        setBackgroundColor(T.page)
        isClickable = true
        val col = a.vbox()
        col.setPadding(T.dp(26), T.dp(26), T.dp(26), 0)
        val top = a.hbox()
        search =
            EditText(a).apply {
                hint = "Search apps"
                setHintTextColor(T.text3)
                setTextColor(T.text)
                textSize = 15f * T.ts
                typeface = T.font(1)
                background = T.round(T.inset, 16)
                padDp(16, 12, 16, 12)
                setSingleLine()
                imeOptions = EditorInfo.IME_ACTION_GO
                addTextChangedListener(
                    object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) {
                            query = s.toString().trim()
                            refresh()
                        }

                        override fun beforeTextChanged(
                            s: CharSequence?,
                            st: Int,
                            c: Int,
                            af: Int,
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            st: Int,
                            b: Int,
                            c: Int,
                        ) {}
                    },
                )
                setOnEditorActionListener { _, id, ev ->
                    if (id == EditorInfo.IME_ACTION_GO || ev?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        shown.firstOrNull()?.let { open(it.pkg) }
                        true
                    } else {
                        false
                    }
                }
            }
        top.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(a.hspace(10))
        top.addView(a.iconBtn("close", T.text2, 44) { close() })
        col.addView(top)
        col.addView(
            Segmented(a, listOf("All apps", "Study", "Hidden"), 0) {
                tab = it
                refresh()
            },
            lp(-2, -2, 12),
        )
        col.addView(extra, lp(-1, -2, 8))
        val body = a.hbox(false)
        grid.apply {
            numColumns =
                Store.i("drawer_cols", 0).let {
                    if (it in 3..9) {
                        it
                    } else if (a.resources.displayMetrics.widthPixels / a.resources.displayMetrics.density > 700) {
                        7
                    } else {
                        4
                    }
                }
            verticalSpacing = T.dp(6)
            selector = android.graphics.drawable.ColorDrawable(0)
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = this@DrawerView.adapter
            setOnItemClickListener { _, _, p, _ -> open(shown[p].pkg) }
            setOnItemLongClickListener { _, _, p, _ ->
                menu(shown[p])
                true
            }
            clipToPadding = false
            setPadding(0, T.dp(8), 0, T.dp(40))
            isVerticalScrollBarEnabled = false
        }
        body.addView(grid, LinearLayout.LayoutParams(0, -1, 1f))
        body.addView(azIndex(), LinearLayout.LayoutParams(T.dp(24), -1))
        col.addView(body, lp(-1, 0, 6, 1f))
        addView(col, LayoutParams(-1, -1))
        refresh()
    }

    private fun azIndex(): View {
        val v = a.vbox().apply { gravity = Gravity.CENTER }
        "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach {
                ch ->
            v.addView(a.text(ch.toString(), 9.5f, 2, T.text3).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        v.setOnTouchListener { _, e ->
            val i = ((e.y / v.height) * 27).toInt().coerceIn(0, 26)
            val ch = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"[i]
            val idx = shown.indexOfFirst { if (ch == '#') !it.label.first().isLetter() else it.label.uppercase().startsWith(ch) }
            if (idx >= 0) grid.setSelection(idx)
            true
        }
        return v
    }

    fun refresh() {
        val all = Apps.list
        val hid = Apps.hidden()
        val study = Apps.study()
        var l =
            when (tab) {
                1 -> all.filter { it.pkg in study && it.pkg !in hid }
                2 -> all.filter { it.pkg in hid }
                else -> all.filter { it.pkg !in hid }
            }
        if (Focus.active()) l = l.filter { Apps.allowedInFocus(a, it.pkg) }
        if (query.isNotEmpty()) l = l.filter { it.label.contains(query, true) }
        shown = l
        adapter.notifyDataSetChanged()
        universal()
    }

    /** Notes, tasks and syllabus topics matching the query, shown above the apps. */
    private fun universal() {
        extra.removeAllViews()
        if (query.length < 2) return

        fun row(
            ic: String,
            tint: Int,
            t: String,
            sub: String,
            f: () -> Unit,
        ) = a.hbox().apply {
            padDp(12, 9, 12, 9)
            background = T.round(T.card, 14)
            addView(a.icon(ic, tint, 18, 1.9f))
            val c2 = a.vbox()
            c2.addView(a.text(t, 13.5f, 1, T.text, 1))
            c2.addView(a.text(sub, 11f, 0, T.text3, 1))
            addView(c2, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = T.dp(10) })
            tap { f() }
        }
        val hits = ArrayList<View>()
        Zite.data(a).syl.forEach {
                s ->
            s.micros.filter { it.name.contains(query, true) }.take(2).forEach {
                    m ->
                hits.add(
                    row("book", T.slate, m.name, "Topic - " + Syl.short(s.name) + " - ask AI") {
                        close()
                        a.startActivity(
                            Intent(
                                a,
                                ChatActivity::class.java,
                            ).putExtra(
                                "q",
                                "Explain " + m.name + " (" + s.name + ") for UPSC Prelims and Mains, with key facts and likely questions.",
                            ),
                        )
                    },
                )
            }
        }
        Store.rows("notes").filter { it.optString("text").contains(query, true) }.take(2).forEach {
                n ->
            hits.add(
                row(
                    "note",
                    T.clay,
                    n.optString("text").take(60),
                    "Note - " + n.optString("date"),
                ) { Dlg.info(a, "Note", n.optString("text")) },
            )
        }
        Repo.allTasks().filter { it.optString("text").contains(query, true) }.take(2).forEach {
                t ->
            hits.add(row("checkc", T.sage, t.optString("text"), "Task - " + t.optString("date")) { close() })
        }
        hits.take(4).forEach { extra.addView(it, lp(-1, -2, 4)) }
        if (shown.isEmpty() || hits.isNotEmpty() || query.length >= 3) {
            extra.addView(
                row("sparkle", T.plum, "Ask Mission AI", query) {
                    close()
                    a.startActivity(Intent(a, ChatActivity::class.java).putExtra("q", query))
                },
                lp(-1, -2, 4),
            )
            extra.addView(
                row("search", T.slate, "Search the web", query) {
                    close()
                    try {
                        a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
                    } catch (
                        x: Exception,
                    ) {
                    }
                },
                lp(-1, -2, 4),
            )
        }
    }

    private fun open(pkg: String) {
        close()
        Apps.launch(a, pkg)
    }

    private fun close() {
        (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
        onClose()
    }

    private fun menu(e: AppEntry) {
        val hid = e.pkg in Apps.hidden()
        val st = e.pkg in Apps.study()
        val dis = e.pkg in Notifs.block()
        val sc =
            try {
                val la = a.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                if (la.hasShortcutHostPermission()) la.getShortcuts(LauncherApps.ShortcutQuery().setPackage(e.pkg).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), Process.myUserHandle())?.take(4) ?: emptyList() else emptyList()
            } catch (
                x: Exception,
            ) {
                emptyList()
            }
        val items = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        sc.forEach {
                s ->
            items.add("> " + (s.shortLabel ?: s.id))
            acts.add {
                close()
                try {
                    (a.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).startShortcut(s, null, null)
                } catch (
                    x: Exception,
                ) {
                    a.toast("Cannot open shortcut")
                }
            }
        }
        items.add("Open in floating window")
        acts.add {
            close()
            Apps.launch(a, e.pkg, "float")
        }
        items.add("Open in split / adjacent")
        acts.add {
            close()
            Apps.launch(a, e.pkg, "split")
        }
        items.add("Daily time limit" + (Guard.limits().optInt(e.pkg, 0).let { if (it > 0) " ($it min)" else "" }))
        acts.add {
            Dlg.input(a, "Daily limit for " + e.label, "minutes, 0 = none", Guard.limits().optInt(e.pkg, 0).toString(), true) { v ->
                v.toIntOrNull()?.let { Guard.setLimit(e.pkg, it) }
                if (!Usage.granted(a)) {
                    a.toast("Grant usage access so limits can work")
                    Usage.openSettings(a)
                }
            }
        }
        items.add(if (dis) "Remove from distraction list" else "Add to distraction list (mute + mindful pause)")
        acts.add {
            val cur = Notifs.block().toMutableSet()
            if (!cur.remove(e.pkg)) cur.add(e.pkg)
            Store.setStringList("block_apps", cur.toList())
            Store.changed()
            a.toast(if (e.pkg in cur) "Added to distraction list" else "Removed")
        }
        items.add("App info")
        acts.add { Apps.info(a, e.pkg) }
        items.add(if (st) "Remove from Study tab" else "Add to Study tab")
        acts.add {
            Apps.toggleList("study_apps", e.pkg)
            refresh()
        }
        items.add(if (hid) "Unhide" else "Hide from drawer")
        acts.add {
            Apps.toggleList("hidden_apps", e.pkg)
            refresh()
        }
        items.add("Uninstall")
        acts.add { Apps.uninstall(a, e.pkg) }
        Dlg.choose(a, e.label, items) { i -> acts[i]() }
    }
}
