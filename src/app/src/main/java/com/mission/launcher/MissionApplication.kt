package com.mission.launcher

import android.app.Application
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
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(filesDir, "crash.log").writeText(
                    "Thread: ${thread.name}\n$trace",
                    StandardCharsets.UTF_8
                )
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
