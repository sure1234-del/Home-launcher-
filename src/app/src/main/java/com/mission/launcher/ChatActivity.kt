package com.mission.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.*

/** Full-screen assistant chat. History is stored forever on the device; the model only sees a rolling summary plus recent turns. */
class ChatActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sub: TextView
    private var busy = false
    private val REQ_VOICE = 91

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Boot.ensure(this)
        systemBars(T.page)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = vbox(); root.setBackgroundColor(T.page)
        root.setOnApplyWindowInsetsListener { v, i -> val s = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime()); v.setPadding(0, s.top, 0, s.bottom); i }
        val bar = hbox(); bar.padDp(8, 8, 8, 8)
        bar.addView(iconBtn("chevl", T.text, 44) { finish() })
        val tc = vbox(); tc.addView(text("Mission AI", 19f, 3)); sub = text("", 12f, 0, T.text3); tc.addView(sub)
        bar.addView(tc, lpm(0, -2, 4, 0, 0, 0, 1f))
        bar.addView(iconBtn("sliders", T.text2, 44) { menu() })
        root.addView(bar)
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        list = vbox(); list.setPadding(T.dp(14), T.dp(6), T.dp(14), T.dp(12)); scroll.addView(list)
        val wide = resources.displayMetrics.widthPixels / resources.displayMetrics.density > 700
        val holder = FrameLayout(this); holder.addView(scroll, FrameLayout.LayoutParams(if (wide) T.dp(760) else -1, -1, Gravity.CENTER_HORIZONTAL))
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = hbox(); row.padDp(12, 6, 12, 10)
        input = Dlg.field(this, "Message", multi = true).apply { minLines = 1; maxLines = 5; background = T.round(T.card, 22); padDp(16, 12, 16, 12); imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION }
        row.addView(iconBtn("mic", T.text2, 44) { voice() })
        row.addView(input, lpm(0, -2, 4, 0, 6, 0, 1f))
        row.addView(iconBtn("up", T.onAccent, 44) { send() }.also { it.background = T.round(T.plum, 22) })
        root.addView(row)
        setContentView(root)
        render()
        intent.getStringExtra("q")?.takeIf { it.isNotBlank() }?.let { ask(it) }
    }

    private fun refreshSub() { val p = Ai.provider(); sub.text = if (Ai.ready(p)) p.name + " - " + Ai.model(p) else "No key set for ${p.name}" }
    private fun render() {
        refreshSub(); list.removeAllViews(); val h = Ai.history()
        if (h.isEmpty()) {
            list.addView(text(if (Ai.ready()) "Ask about your plan, a topic, an answer to review, or tell me to add a task, note, water or start the timer." else "Add an API key in Settings > AI assistants to chat here. Gemini keys are free from aistudio.google.com/apikey.", 14f, 0, T.text3).apply { padDp(10, 30, 10, 10) })
            if (!Ai.ready()) list.addView(pill("Open AI settings", "gear", true, T.plum) { startActivity(Intent(this@ChatActivity, SettingsActivity::class.java).putExtra("path", "ai")) }, lp(-2, -2, 10))
        }
        h.forEach { bubble(it.optString("role") == "user", it.optString("text")) }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
    private fun md(s: String): CharSequence {
        val sb = SpannableStringBuilder(); var i = 0
        val clean = s.replace(Regex("(?m)^#{1,6}\\s*"), "").replace(Regex("(?m)^\\s*[*-]\\s+"), "- ")
        while (i < clean.length) {
            val a = clean.indexOf("**", i); if (a < 0) { sb.append(clean.substring(i)); break }
            val b = clean.indexOf("**", a + 2); if (b < 0) { sb.append(clean.substring(i)); break }
            sb.append(clean.substring(i, a)); val st = sb.length; sb.append(clean.substring(a + 2, b)); sb.setSpan(StyleSpan(Typeface.BOLD), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i = b + 2
        }
        return sb
    }
    private fun bubble(user: Boolean, t: String): TextView {
        val tv = text("", 15f, 0, if (user) T.onAccent else T.text).apply { setText(md(t)); setLineSpacing(0f, 1.15f); setTextIsSelectable(true); padDp(14, 10, 14, 10); background = T.round(if (user) T.plum else T.card, 20); maxWidth = (resources.displayMetrics.widthPixels * (if (resources.displayMetrics.widthPixels / resources.displayMetrics.density > 700) 0.5 else 0.82)).toInt() }
        val lp = LinearLayout.LayoutParams(-2, -2); lp.topMargin = T.dp(8); lp.gravity = if (user) Gravity.END else Gravity.START; list.addView(tv, lp); return tv
    }
    private fun send() { val s = input.text.toString().trim(); if (s.isEmpty() || busy) return; input.setText(""); ask(s) }
    private fun ask(q: String) {
        if (busy) return
        if (!Ai.ready()) { toast("Add an AI key in Settings first"); startActivity(Intent(this, SettingsActivity::class.java).putExtra("path", "ai")); return }
        busy = true; if (Ai.history().isEmpty()) list.removeAllViews()
        bubble(true, q); val wait = bubble(false, "Thinking..."); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        Thread {
            val r = try { Ai.ask(this, q) } catch (e: Exception) { "Error: " + (e.message ?: e.toString()) }
            runOnUiThread { busy = false; wait.text = md(r); wait.setTextColor(T.text); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }; Store.changed() }
        }.start()
    }
    private fun voice() {
        try { startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false), REQ_VOICE) } catch (e: Exception) { toast("Voice input not available on this device") }
    }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, d: Intent?) {
        super.onActivityResult(req, res, d)
        if (req == REQ_VOICE && res == RESULT_OK) d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input.setText(it); input.setSelection(it.length) }
    }
    private fun menu() {
        Dlg.choose(this, "Chat", listOf("Switch assistant", "Share last answer", "Open Xiaomi / system assistant", "Clear chat", "AI settings")) { i ->
            when (i) {
                0 -> Dlg.choose(this, "Assistant", Ai.providers.map { it.name }) { k -> Store.put("ai_provider", Ai.providers[k].id); refreshSub() }
                1 -> Ai.history().lastOrNull { it.optString("role") != "user" }?.let { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it.optString("text")), "Share")) }
                2 -> if (!Ai.assist(this)) toast("No assistant found")
                3 -> Dlg.confirm(this, "Clear chat?", "All messages are removed from this device.", "Clear") { Ai.clearHistory(); render() }
                4 -> startActivity(Intent(this, SettingsActivity::class.java).putExtra("path", "ai"))
            }
        }
    }
    override fun onResume() { super.onResume(); refreshSub() }
}
