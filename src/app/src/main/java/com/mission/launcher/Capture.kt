package com.mission.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast

/** Share-sheet / quick-tile capture: text becomes a note, task or revision topic. */
class CaptureActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Boot.ensure(this)
        val e =
            EditText(this).apply {
                hint = "Type or paste"
                setText(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "")
                minLines = 3
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setPadding(48, 24, 48, 24)
            }

        fun txt() = e.text.toString().trim()

        fun done(m: String) {
            Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
            finish()
        }
        val d =
            AlertDialog.Builder(this).setTitle("Quick capture").setView(e)
                .setPositiveButton("Note") { _, _ ->
                    if (txt().isNotEmpty()) {
                        Store.add("notes", obj("date" to Store.today(), "text" to txt(), "t" to System.currentTimeMillis()))
                        done("Saved to notes")
                    } else {
                        finish()
                    }
                }
                .setNeutralButton("Task") { _, _ ->
                    if (txt().isNotEmpty()) {
                        Repo.addTask(txt())
                        done("Task added for today")
                    } else {
                        finish()
                    }
                }
                .setNegativeButton("Revise") { _, _ ->
                    if (txt().isNotEmpty()) {
                        Repo.addRevision(txt().take(80))
                        done("Added to revision list")
                    } else {
                        finish()
                    }
                }
                .setOnCancelListener { finish() }.show()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
}

class CaptureTile : TileService() {
    override fun onStartListening() {
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        val i = Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}

/** Quick-settings tile that starts/stops the floating study timer. */
class TimerTile : TileService() {
    override fun onStartListening() {
        qsTile?.state = if (TimerState.active()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.label = if (TimerState.active()) "Study: " + TimerState.subject() else "Study timer"
        qsTile?.updateTile()
    }

    override fun onClick() {
        Boot.ensure(this)
        if (TimerState.active()) {
            TimerState.finish(true)
            TimerService.stop(this)
        } else {
            TimerState.start(Store.s("timer_subject", "General"), Store.s("timer_mode", "stopwatch"))
            TimerService.start(this)
        }
        onStartListening()
    }
}
