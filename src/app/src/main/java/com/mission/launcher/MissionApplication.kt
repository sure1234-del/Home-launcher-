package com.mission.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Process
import android.os.StrictMode
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

class MissionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        MissionSyncWorker.schedule(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            Apps.iconCacheClear?.invoke()
            try {
                MissionWallpaperService.releaseCachedFrame()
            } catch (_: Exception) {
            }
        }
        try {
            val prefs = getSharedPreferences("mission_diagnostics", MODE_PRIVATE)
            prefs.edit().putLong("last_trim", System.currentTimeMillis()).putInt("last_trim_level", level).apply()
        } catch (_: Exception) {
        }
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(filesDir, "crash.log").writeText(
                    "Thread: ${thread.name}\n$trace",
                    StandardCharsets.UTF_8,
                )
                val prefs = getSharedPreferences("mission_diagnostics", MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val first = prefs.getLong("crash_window_first", 0L)
                val window = if (first <= 0L || now - first > 5 * 60 * 1000L) now else first
                val count = if (window == now) 1 else prefs.getInt("crash_window_count", 0) + 1
                prefs.edit().putLong("crash_window_first", window).putInt("crash_window_count", count)
                    .putBoolean("safe_mode", count >= 3).apply()
            } catch (loggingError: Throwable) {
                Log.e("MissionLauncher", "Unable to write the local crash log", loggingError)
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, error)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }
}
