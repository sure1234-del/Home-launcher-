package com.mission.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.time.temporal.ChronoUnit

class MissionWallpaperService : WallpaperService() {
    companion object {
        @Volatile private var frameRelease: (() -> Unit)? = null
        fun releaseCachedFrame() {
            frameRelease?.invoke()
        }
    }
    override fun onCreateEngine(): Engine = MissionEngine()

    private inner class MissionEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var frame: Bitmap? = null
        private var width = 0
        private var height = 0
        private var offsetX = 0f

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                val fps = Store.i("wp_fps", 30).coerceIn(1, 30)
                val delay = if (Store.b("wp_animation", true)) 1000L / fps else 60_000L
                if (visible) handler.postDelayed(this, delay)
            }
        }
        private val idleRunnable = Runnable {
            if (!visible) recycleFrame()
        }

        init {
            frameRelease = { releaseFrame() }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(drawRunnable)
            handler.removeCallbacks(idleRunnable)
            if (visible) {
                handler.post(drawRunnable)
            } else {
                handler.postDelayed(idleRunnable, 60_000L)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            recycleFrame()
            drawFrame()
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            offsetX = xOffset
            if (visible) drawFrame()
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
            recycleFrame()
            frameRelease = null
            super.onDestroy()
        }

        private fun recycleFrame() {
            frame?.let { if (!it.isRecycled) it.recycle() }
            frame = null
        }

        fun releaseFrame() {
            handler.removeCallbacks(drawRunnable)
            recycleFrame()
        }

        private fun drawFrame() {
            if (!visible || width <= 0 || height <= 0) return
            val power = getSystemService(POWER_SERVICE) as PowerManager
            val batterySaver = Store.b("wp_battery_pause", true) && power.isPowerSaveMode
            val renderWidth = (width * 1.12f).toInt().coerceAtMost(3840)
            val renderHeight = height.coerceAtMost(2400)

            try {
                if (frame == null || frame!!.width != renderWidth || frame!!.height != renderHeight) {
                    recycleFrame()
                    val today = TimeProvider.today()
                    frame = Scenes.render(
                        renderWidth,
                        renderHeight,
                        Wallpaper.hourNow(),
                        Wallpaper.scene(),
                        today.monthValue,
                        WOpts(
                            Store.b("wp_grain", true),
                            Store.b("wp_comfort", true),
                            Store.b("wp_season", true),
                            if (batterySaver) 0.40f else Store.i("wp_dim", 6) / 100f
                        ),
                        today.dayOfYear,
                        today,
                        Weather.forRendering()
                    )
                }
            } catch (oom: OutOfMemoryError) {
                recycleFrame()
                return
            }

            var canvas: Canvas? = null
            try {
                canvas = if (android.os.Build.VERSION.SDK_INT >= 26) surfaceHolder.lockHardwareCanvas()
                else surfaceHolder.lockCanvas()
                val activeFrame = frame
                if (canvas != null && activeFrame != null) {
                    canvas.drawBitmap(activeFrame, -offsetX * renderWidth * 0.12f, 0f, null)
                    if (Store.b("wp_lock_text", true)) {
                        val days = ChronoUnit.DAYS.between(TimeProvider.today(), Repo.examDate())
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE
                            textAlign = Paint.Align.CENTER
                            textSize = renderHeight * 0.032f
                        }
                        canvas.drawText("$days days to Prelims", renderWidth / 2f, renderHeight * 0.93f, paint)
                    }
                }
            } catch (e: Exception) {
                Log.e("live-wallpaper", e)
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
