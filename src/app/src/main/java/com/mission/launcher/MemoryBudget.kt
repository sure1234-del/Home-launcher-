package com.mission.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.Debug

object MemoryBudget {
    const val PSS_CEILING_KB = 280 * 1024
    const val LARGE_BITMAP_LIMIT = 3

    fun iconCache(memoryClassMb: Int): Int = (memoryClassMb.coerceIn(64, 256) * 64).coerceIn(4096, 16384)

    fun widgetPreviewCache(memoryClassMb: Int): Int = (memoryClassMb.coerceIn(64, 256) * 24).coerceIn(1536, 6144)

    fun assertBudget(context: Context) {
        if (!BuildConfig.DEBUG) return
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        check(info.totalPss * 1024 <= PSS_CEILING_KB) {
            "Mission PSS budget exceeded: ${info.totalPss} KB"
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClass = am.memoryClass
        check(memoryClass > 0) { "Invalid ActivityManager memory class" }
    }
}
