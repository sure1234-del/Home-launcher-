package com.mission.launcher

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.text.TextWatcher
import android.text.Editable
import java.util.Locale

object WidgetCatalogDialog {
    data class Option(val id: String, val title: String, val subtitle: String = "")

    fun show(context: Context, options: List<Option>, onSelect: (Option) -> Unit) {
        val allOptions = options.toList()
        val search = Dlg.field(context, "Search widgets").apply { isSingleLine = true }
        val emptyMessage = context.text("No widgets available.", 14f, 0, T.text3).apply {
            gravity = android.view.Gravity.CENTER
            padDp(16, 24, 16, 24)
            visibility = View.GONE
        }
        val list = ListView(context).apply {
            divider = android.graphics.drawable.ColorDrawable(T.line)
            dividerHeight = maxOf(1, T.dpFixed(1))
            choiceMode = ListView.CHOICE_MODE_NONE
            isVerticalScrollBarEnabled = true
        }

        var visibleOptions = allOptions
        val adapter = object : BaseAdapter() {
            override fun getCount() = visibleOptions.size
            override fun getItem(position: Int) = visibleOptions[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val option = getItem(position)
                val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    padDp(14, 12, 14, 12)
                    addView(context.text("", 15f, 2, T.text), LinearLayout.LayoutParams(-1, -2))
                    addView(context.text("", 12.5f, 0, T.text2), LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = T.dp(4)
                    })
                }
                (row.getChildAt(0) as TextView).text = option.title
                (row.getChildAt(1) as TextView).apply {
                    text = option.subtitle
                    visibility = if (option.subtitle.isBlank()) View.GONE else View.VISIBLE
                }
                return row
            }
        }
        list.adapter = adapter

        fun updateResults(query: String) {
            val needle = query.trim().lowercase(Locale.ROOT)
            visibleOptions = if (needle.isEmpty()) allOptions else allOptions.filter { option ->
                option.title.lowercase(Locale.ROOT).contains(needle) ||
                    option.subtitle.lowercase(Locale.ROOT).contains(needle)
            }
            adapter.notifyDataSetChanged()
            if (visibleOptions.isEmpty()) {
                emptyMessage.text = if (allOptions.isEmpty()) "No widgets available." else "No matching widgets."
                emptyMessage.visibility = View.VISIBLE
                list.visibility = View.GONE
            } else {
                emptyMessage.visibility = View.GONE
                list.visibility = View.VISIBLE
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val content = context.vbox().apply {
            padDp(22, 8, 22, 0)
            addView(search, lp(-1, -2))
            addView(emptyMessage, lp(-1, T.dp(220), 8))
            addView(list, lp(-1, T.dp(320), 8))
        }
        updateResults("")

        val dialog: AlertDialog = Dlg.builder(context)
            .setTitle("Widget catalog")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            visibleOptions.getOrNull(position)?.let { option ->
                dialog.dismiss()
                onSelect(option)
            }
        }
        dialog.show()
    }
}
