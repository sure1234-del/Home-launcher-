package com.mission.launcher

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.LocalDate

object Dlg {
    private fun theme() = if (T.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
    fun builder(c: Context) = AlertDialog.Builder(c, theme())
    fun field(c: Context, hint: String, init: String = "", numeric: Boolean = false, multi: Boolean = false, password: Boolean = false): EditText = EditText(c).apply {
        this.hint = hint; setText(init); setTextColor(T.text); setHintTextColor(T.text3); typeface = T.font(1); textSize = 15f * T.ts
        inputType = (if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or (if (multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)) or (if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD else 0)
        if (password && !numeric) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        if (multi) minLines = 3; background = T.round(T.inset, 12); padDp(14, 12, 14, 12)
    }
    private fun wrap(c: Context, v: View): View = LinearLayout(c).apply { setPadding(T.dp(22), T.dp(8), T.dp(22), 0); addView(v, LinearLayout.LayoutParams(-1, -2)) }
    fun input(c: Context, title: String, hint: String = "", init: String = "", numeric: Boolean = false, multi: Boolean = false, ok: String = "Save", cb: (String) -> Unit) {
        val e = field(c, hint, init, numeric, multi)
        val d = builder(c).setTitle(title).setView(wrap(c, e)).setPositiveButton(ok) { _, _ -> cb(e.text.toString().trim()) }.setNegativeButton("Cancel", null).create()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE); d.show()
    }
    fun confirm(c: Context, title: String, msg: String, ok: String = "Yes", cb: () -> Unit) { builder(c).setTitle(title).setMessage(msg).setPositiveButton(ok) { _, _ -> cb() }.setNegativeButton("Cancel", null).show() }
    fun choose(c: Context, title: String, items: List<String>, cb: (Int) -> Unit) { builder(c).setTitle(title).setItems(items.toTypedArray()) { _, i -> cb(i) }.show() }
    fun info(c: Context, title: String, msg: String) { builder(c).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show() }
    fun view(c: Context, title: String, v: View, ok: String? = "Close", cb: (() -> Unit)? = null) {
        val sv = ScrollView(c).apply { addView(wrap(c, v)) }
        val b = builder(c).setTitle(title).setView(sv); if (ok != null) b.setPositiveButton(ok) { _, _ -> cb?.invoke() }; b.show()
    }
    fun date(c: Context, init: LocalDate = LocalDate.now(), cb: (LocalDate) -> Unit) { DatePickerDialog(c, { _, y, m, d -> cb(LocalDate.of(y, m + 1, d)) }, init.year, init.monthValue - 1, init.dayOfMonth).show() }
    fun time(c: Context, cb: (String) -> Unit) { TimePickerDialog(c, { _, h, m -> cb("%02d:%02d".format(h, m)) }, 9, 0, true).show() }

    /** Add-event dialog (study-relevant only). */
    fun addEvent(c: Context) {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val t = field(c, "Title (test, class, deadline)"); box.addView(t)
        var date = LocalDate.now(); var time = ""
        val dBtn = c.pill("Date: $date", "calendar") { }; val tBtn = c.pill("Time: none", "clock") { }
        box.addView(dBtn, lp(-2, -2, 10)); box.addView(tBtn, lp(-2, -2, 8))
        (dBtn.getChildAt(2) as android.widget.TextView).let { tv -> dBtn.setOnClickListener { date(c, date) { d -> date = d; tv.text = "Date: $d" } } }
        (tBtn.getChildAt(2) as android.widget.TextView).let { tv -> tBtn.setOnClickListener { time(c) { x -> time = x; tv.text = "Time: $x" } } }
        view(c, "Add to Upcoming", box, "Add") { if (t.text.isNotBlank()) Store.add("events", obj("title" to t.text.toString().trim(), "date" to date.toString(), "time" to time)) }
    }
}
