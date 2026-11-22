package com.mission.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityButtonController
import android.app.Activity
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Accessibility service used for: Circle to Search (screenshot + lasso), lock screen, notification shade.
 * It does not read screen content (canRetrieveWindowContent is off); it only takes a screenshot when you ask.
 */
class CircleService : AccessibilityService() {
    companion object {
        @Volatile var inst: CircleService? = null
        fun running() = inst != null
        fun enabled(c: Context): Boolean = try { (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(c.packageName + "/") } catch (e: Exception) { false }
        fun openSettings(c: Context) { try { c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { } }
        fun need(c: Context) { Toast.makeText(c, "Turn on Mission accessibility service first", Toast.LENGTH_LONG).show(); openSettings(c) }
        fun capture(c: Context) { val s = inst; if (s == null) need(c) else s.snap() }
        fun lock(c: Context): Boolean { val s = inst ?: run { need(c); return false }; return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }
        fun shade(c: Context): Boolean {
            try { val sb = c.getSystemService("statusbar"); Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb); return true } catch (e: Throwable) { }
            val s = inst ?: run { need(c); return false }; return s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
    }
    private var overlay: View? = null
    private val ui = Handler(Looper.getMainLooper())
    private val btn = object : AccessibilityButtonController.AccessibilityButtonCallback() { override fun onClicked(c: AccessibilityButtonController) { snap() } }

    override fun onServiceConnected() {
        super.onServiceConnected(); inst = this; Boot.ensure(this)
        try { accessibilityButtonController.registerAccessibilityButtonCallback(btn) } catch (e: Throwable) { }
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(i: Intent?): Boolean { inst = null; closeOverlay(); return super.onUnbind(i) }

    fun snap() {
        if (overlay != null) return
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(r: ScreenshotResult) {
                    val hb = r.hardwareBuffer
                    val bm = try { Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) } finally { hb.close() }
                    if (bm == null) toast("Screenshot failed") else showLasso(bm)
                }
                override fun onFailure(code: Int) { toast(if (code == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) "Wait a second and try again" else "Screenshot blocked on this screen") }
            })
        } catch (e: Throwable) { Log.e("circle", e); toast("Circle to Search is not available") }
    }
    private fun toast(s: String) { ui.post { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() } }
    private fun add(v: View) {
        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(v, lp); overlay = v
    }
    private fun closeOverlay() { overlay?.let { try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } catch (e: Exception) { } }; overlay = null }

    private fun showLasso(bm: Bitmap) { ui.post { add(Lasso(this, bm, { crop -> closeOverlay(); if (crop != null) showResult(crop) }, { closeOverlay() })) } }

    private fun showResult(crop: Bitmap) {
        val root = FrameLayout(this); root.setBackgroundColor(0xCC000000.toInt()); root.isClickable = true
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; padDp(20, 20, 20, 20); background = T.round(T.cardSolid, 28) }
        col.addView(ImageView(this).apply { setImageBitmap(crop); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; background = T.round(T.inset, 14); padDp(6, 6, 6, 6) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = hbox().apply { gravity = Gravity.CENTER }
        var uri: Uri? = null
        fun saved(): Uri? { if (uri == null) uri = saveImage(crop); return uri }
        row.addView(pill("Google Lens", "search", true, T.slate) { val u = saved(); if (u != null) { closeOverlay(); sendToLens(u) } else toast("Could not save image") }, lpm(-2, -2, 0, 0, 8, 0))
        row.addView(pill("Share", "up", false) { val u = saved(); if (u != null) { closeOverlay(); share(u, null) } }, lpm(-2, -2, 0, 0, 8, 0))
        row.addView(pill("Save", "down", false) { toast(if (saved() != null) "Saved in Pictures/Mission" else "Could not save") }, lpm(-2, -2, 0, 0, 8, 0))
        row.addView(pill("Close", "close", false) { closeOverlay() })
        col.addView(chipRow(row), lp(-1, -2, 14))
        val w = resources.displayMetrics.widthPixels; val h = resources.displayMetrics.heightPixels
        root.addView(col, FrameLayout.LayoutParams(minOf(w - T.dp(32), T.dp(520)), (h * 0.7f).toInt(), Gravity.CENTER))
        ui.post { add(root) }
    }
    private fun saveImage(bm: Bitmap): Uri? = try {
        val cv = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "mission_circle_${System.currentTimeMillis()}.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mission"); put(MediaStore.Images.Media.IS_PENDING, 1) }
        val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
        contentResolver.openOutputStream(u)!!.use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cv.clear(); cv.put(MediaStore.Images.Media.IS_PENDING, 0); contentResolver.update(u, cv, null, null); u
    } catch (e: Throwable) { Log.e("circle-save", e); null }
    private fun intentFor(u: Uri) = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    private fun sendToLens(u: Uri) {
        for (pkg in listOf("com.google.android.googlequicksearchbox", "com.google.ar.lens")) {
            val i = intentFor(u).setPackage(pkg)
            if (packageManager.queryIntentActivities(i, 0).isNotEmpty()) { try { startActivity(i); return } catch (e: Exception) { } }
        }
        share(u, "Search with")
    }
    private fun share(u: Uri, t: String?) { try { startActivity(Intent.createChooser(intentFor(u), t ?: "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast("No app to handle the image") } }
}

/** Full-screen frozen screenshot where the user draws a loop around what to search. */
private class Lasso(c: Context, val bm: Bitmap, val done: (Bitmap?) -> Unit, val cancel: () -> Unit) : View(c) {
    private val path = Path(); private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = T.dpf(5f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0xFFFFFFFF.toInt() }
    private val halo = Paint(glow).apply { strokeWidth = T.dpf(12f); color = 0x55FFFFFF }
    private val dim = Paint().apply { color = 0x66000000 }; private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = T.dpf(15f); textAlign = Paint.Align.CENTER; typeface = T.font(2) }
    private var l = 1e9f; private var t = 1e9f; private var r = 0f; private var b = 0f; private var drawing = false; private var any = false
    private val src = Rect(0, 0, bm.width, bm.height)
    override fun onDraw(cv: Canvas) {
        cv.drawBitmap(bm, src, Rect(0, 0, width, height), null); cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        if (!any) { cv.drawText("Draw a circle around anything to search", width / 2f, T.dpf(90f), hint); cv.drawText("Tap outside to cancel", width / 2f, T.dpf(114f), hint.apply { alpha = 170 }); hint.alpha = 255 }
        else { cv.save(); cv.clipRect(l, t, r, b); cv.drawBitmap(bm, src, Rect(0, 0, width, height), null); cv.restore(); cv.drawPath(path, halo); cv.drawPath(path, glow) }
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { path.reset(); path.moveTo(e.x, e.y); l = e.x; r = e.x; t = e.y; b = e.y; drawing = true; any = false }
            MotionEvent.ACTION_MOVE -> { path.lineTo(e.x, e.y); l = minOf(l, e.x); r = maxOf(r, e.x); t = minOf(t, e.y); b = maxOf(b, e.y); any = true; invalidate() }
            MotionEvent.ACTION_UP -> {
                if (!any || (r - l < T.dp(36) && b - t < T.dp(36))) { cancel(); return true }
                val pad = T.dp(18); val sx = bm.width / width.toFloat(); val sy = bm.height / height.toFloat()
                val x0 = ((l - pad) * sx).toInt().coerceIn(0, bm.width - 2); val y0 = ((t - pad) * sy).toInt().coerceIn(0, bm.height - 2)
                val x1 = ((r + pad) * sx).toInt().coerceIn(x0 + 1, bm.width); val y1 = ((b + pad) * sy).toInt().coerceIn(y0 + 1, bm.height)
                done(try { Bitmap.createBitmap(bm, x0, y0, x1 - x0, y1 - y0) } catch (ex: Exception) { null })
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }
}

/** Quick Settings tile: collapse the shade, then capture. */
class CircleTile : TileService() {
    override fun onStartListening() { qsTile?.state = Tile.STATE_INACTIVE; qsTile?.label = "Circle to Search"; qsTile?.updateTile() }
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        val i = Intent(this, CircleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE)) else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}
class CircleActivity : Activity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); Boot.ensure(this); Handler(Looper.getMainLooper()).postDelayed({ CircleService.capture(applicationContext); finish() }, 500) }
}
