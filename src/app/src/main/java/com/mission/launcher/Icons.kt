package com.mission.launcher

import android.content.Context
import android.graphics.*
import android.view.View

/** Thin line icons drawn from paths on a 24x24 grid (no emoji, so they render identically everywhere). */
object Icons {
    private fun p(f: Path.() -> Unit): Path = Path().apply(f)

    private val cache = HashMap<String, Path>()

    fun path(n: String): Path = cache.getOrPut(n) { build(n) }

    private fun build(n: String): Path =
        when (n) {
            "drop" ->
                p {
                    moveTo(12f, 3f)
                    cubicTo(9f, 7f, 5.5f, 10.5f, 5.5f, 14.5f)
                    arcTo(RectF(5.5f, 8f, 18.5f, 21f), 180f, -180f)
                    cubicTo(18.5f, 10.5f, 15f, 7f, 12f, 3f)
                    close()
                }
            "clock" ->
                p {
                    addCircle(12f, 12f, 9f, Path.Direction.CW)
                    moveTo(12f, 7f)
                    lineTo(12f, 12f)
                    lineTo(15.5f, 14f)
                }
            "check" ->
                p {
                    moveTo(5f, 12.5f)
                    lineTo(10f, 17f)
                    lineTo(19f, 7f)
                }
            "checkc" ->
                p {
                    addCircle(12f, 12f, 9f, Path.Direction.CW)
                    moveTo(8f, 12.5f)
                    lineTo(11f, 15.5f)
                    lineTo(16.5f, 9f)
                }
            "circle" -> p { addCircle(12f, 12f, 9f, Path.Direction.CW) }
            "book" ->
                p {
                    moveTo(4f, 5f)
                    lineTo(11f, 6.5f)
                    lineTo(11f, 19f)
                    lineTo(4f, 17.5f)
                    close()
                    moveTo(20f, 5f)
                    lineTo(13f, 6.5f)
                    lineTo(13f, 19f)
                    lineTo(20f, 17.5f)
                    close()
                }
            "flame" ->
                p {
                    moveTo(12f, 3f)
                    cubicTo(13f, 7f, 18f, 9f, 18f, 14.5f)
                    cubicTo(18f, 18f, 15.3f, 21f, 12f, 21f)
                    cubicTo(8.7f, 21f, 6f, 18.2f, 6f, 15f)
                    cubicTo(6f, 12.5f, 7.6f, 11.3f, 8.5f, 9.5f)
                    cubicTo(9.5f, 11f, 10.2f, 11.2f, 10.8f, 10.8f)
                    cubicTo(11.5f, 8.5f, 10.8f, 5.5f, 12f, 3f)
                    close()
                }
            "gear" ->
                p {
                    addCircle(12f, 12f, 3f, Path.Direction.CW)
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0)
                        val c = Math.cos(a).toFloat()
                        val s = Math.sin(a).toFloat()
                        moveTo(12f + c * 6.5f, 12f + s * 6.5f)
                        lineTo(12f + c * 9f, 12f + s * 9f)
                    }
                    addCircle(12f, 12f, 6.5f, Path.Direction.CW)
                }
            "plus" ->
                p {
                    moveTo(12f, 5f)
                    lineTo(12f, 19f)
                    moveTo(5f, 12f)
                    lineTo(19f, 12f)
                }
            "minus" ->
                p {
                    moveTo(5f, 12f)
                    lineTo(19f, 12f)
                }
            "chart" ->
                p {
                    moveTo(4f, 20f)
                    lineTo(20f, 20f)
                    moveTo(7f, 17f)
                    lineTo(7f, 11f)
                    moveTo(12f, 17f)
                    lineTo(12f, 6f)
                    moveTo(17f, 17f)
                    lineTo(17f, 13f)
                }
            "trend" ->
                p {
                    moveTo(3.5f, 17f)
                    lineTo(9f, 11f)
                    lineTo(13f, 14.5f)
                    lineTo(20.5f, 6.5f)
                    moveTo(15.5f, 6.5f)
                    lineTo(20.5f, 6.5f)
                    lineTo(20.5f, 11.5f)
                }
            "bell" ->
                p {
                    moveTo(6f, 16f)
                    lineTo(6f, 11f)
                    cubicTo(6f, 7.5f, 8.5f, 5.5f, 12f, 5.5f)
                    cubicTo(15.5f, 5.5f, 18f, 7.5f, 18f, 11f)
                    lineTo(18f, 16f)
                    lineTo(19.5f, 17.5f)
                    lineTo(4.5f, 17.5f)
                    close()
                    moveTo(10f, 20f)
                    lineTo(14f, 20f)
                }
            "calendar" ->
                p {
                    addRoundRect(RectF(4f, 5.5f, 20f, 20f), 3f, 3f, Path.Direction.CW)
                    moveTo(4f, 10f)
                    lineTo(20f, 10f)
                    moveTo(8.5f, 3.5f)
                    lineTo(8.5f, 7f)
                    moveTo(15.5f, 3.5f)
                    lineTo(15.5f, 7f)
                }
            "target" ->
                p {
                    addCircle(12f, 12f, 9f, Path.Direction.CW)
                    addCircle(12f, 12f, 5f, Path.Direction.CW)
                    addCircle(12f, 12f, 1.2f, Path.Direction.CW)
                }
            "pencil" ->
                p {
                    moveTo(5f, 19f)
                    lineTo(6f, 15f)
                    lineTo(16f, 5f)
                    lineTo(19f, 8f)
                    lineTo(9f, 18f)
                    close()
                    moveTo(14f, 7f)
                    lineTo(17f, 10f)
                }
            "trash" ->
                p {
                    moveTo(5f, 7f)
                    lineTo(19f, 7f)
                    moveTo(9f, 7f)
                    lineTo(9f, 4.5f)
                    lineTo(15f, 4.5f)
                    lineTo(15f, 7f)
                    moveTo(6.5f, 7f)
                    lineTo(7.5f, 19.5f)
                    lineTo(16.5f, 19.5f)
                    lineTo(17.5f, 7f)
                    moveTo(10.5f, 10.5f)
                    lineTo(10.5f, 16f)
                    moveTo(13.5f, 10.5f)
                    lineTo(13.5f, 16f)
                }
            "refresh" ->
                p {
                    arcTo(RectF(5f, 5f, 19f, 19f), -40f, 270f)
                    moveTo(17.5f, 3.5f)
                    lineTo(18.5f, 7.5f)
                    lineTo(14.5f, 8.2f)
                }
            "search" ->
                p {
                    addCircle(10.5f, 10.5f, 6f, Path.Direction.CW)
                    moveTo(15f, 15f)
                    lineTo(20f, 20f)
                }
            "mic" ->
                p {
                    addRoundRect(RectF(9f, 3.5f, 15f, 14f), 3f, 3f, Path.Direction.CW)
                    moveTo(6f, 11f)
                    cubicTo(6f, 15f, 8.5f, 17f, 12f, 17f)
                    cubicTo(15.5f, 17f, 18f, 15f, 18f, 11f)
                    moveTo(12f, 17f)
                    lineTo(12f, 20.5f)
                }
            "sparkle" ->
                p {
                    moveTo(12f, 3f)
                    cubicTo(12.6f, 8f, 14f, 10.4f, 21f, 12f)
                    cubicTo(14f, 13.6f, 12.6f, 16f, 12f, 21f)
                    cubicTo(11.4f, 16f, 10f, 13.6f, 3f, 12f)
                    cubicTo(10f, 10.4f, 11.4f, 8f, 12f, 3f)
                    close()
                }
            "folder" ->
                p {
                    moveTo(3.5f, 7f)
                    lineTo(3.5f, 18f)
                    lineTo(20.5f, 18f)
                    lineTo(20.5f, 8.5f)
                    lineTo(11f, 8.5f)
                    lineTo(9.5f, 6f)
                    lineTo(3.5f, 6f)
                    close()
                }
            "chev" ->
                p {
                    moveTo(9f, 5f)
                    lineTo(16f, 12f)
                    lineTo(9f, 19f)
                }
            "chevd" ->
                p {
                    moveTo(5f, 9f)
                    lineTo(12f, 16f)
                    lineTo(19f, 9f)
                }
            "chevl" ->
                p {
                    moveTo(15f, 5f)
                    lineTo(8f, 12f)
                    lineTo(15f, 19f)
                }
            "chevu" ->
                p {
                    moveTo(5f, 15f)
                    lineTo(12f, 8f)
                    lineTo(19f, 15f)
                }
            "lock" ->
                p {
                    addRoundRect(RectF(5f, 10.5f, 19f, 20f), 3f, 3f, Path.Direction.CW)
                    moveTo(8f, 10.5f)
                    lineTo(8f, 8f)
                    cubicTo(8f, 5.5f, 10f, 4f, 12f, 4f)
                    cubicTo(14f, 4f, 16f, 5.5f, 16f, 8f)
                    lineTo(16f, 10.5f)
                }
            "timer" ->
                p {
                    addCircle(12f, 13.5f, 7.5f, Path.Direction.CW)
                    moveTo(12f, 13.5f)
                    lineTo(12f, 9.5f)
                    moveTo(9.5f, 3.5f)
                    lineTo(14.5f, 3.5f)
                    moveTo(18.5f, 7f)
                    lineTo(19.8f, 5.7f)
                }
            "cloud" ->
                p {
                    moveTo(7f, 18f)
                    cubicTo(4f, 18f, 3f, 14.5f, 6f, 13.2f)
                    cubicTo(6f, 9.5f, 11f, 8f, 13f, 11f)
                    cubicTo(16f, 9.5f, 19f, 12f, 17.5f, 14.8f)
                    cubicTo(20.5f, 15f, 20.5f, 18f, 17.5f, 18f)
                    close()
                }
            "sun" ->
                p {
                    addCircle(12f, 12f, 4f, Path.Direction.CW)
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0)
                        val c = Math.cos(a).toFloat()
                        val s = Math.sin(a).toFloat()
                        moveTo(12f + c * 7f, 12f + s * 7f)
                        lineTo(12f + c * 9.5f, 12f + s * 9.5f)
                    }
                }
            "moon" ->
                p {
                    moveTo(19f, 14.5f)
                    cubicTo(13f, 17.5f, 6.5f, 13f, 8f, 5.5f)
                    cubicTo(3.5f, 7.5f, 4f, 15f, 9.5f, 18.5f)
                    cubicTo(13f, 20.5f, 17.5f, 18.5f, 19f, 14.5f)
                    close()
                }
            "grid" ->
                p {
                    for (i in 0 until 3) for (j in 0 until 3) addRoundRect(
                        RectF(4.5f + i * 5.5f, 4.5f + j * 5.5f, 8.5f + i * 5.5f, 8.5f + j * 5.5f),
                        1.2f,
                        1.2f,
                        Path.Direction.CW,
                    )
                }
            "close" ->
                p {
                    moveTo(6f, 6f)
                    lineTo(18f, 18f)
                    moveTo(18f, 6f)
                    lineTo(6f, 18f)
                }
            "star" ->
                p {
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) 9f else 4f
                        val a = Math.toRadians(-90.0 + i * 36.0)
                        val x = 12f + r * Math.cos(a).toFloat()
                        val y = 12.5f + r * Math.sin(a).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
            "dots" ->
                p {
                    addCircle(6f, 12f, 1.3f, Path.Direction.CW)
                    addCircle(12f, 12f, 1.3f, Path.Direction.CW)
                    addCircle(18f, 12f, 1.3f, Path.Direction.CW)
                }
            "list" ->
                p {
                    moveTo(8f, 7f)
                    lineTo(20f, 7f)
                    moveTo(8f, 12f)
                    lineTo(20f, 12f)
                    moveTo(8f, 17f)
                    lineTo(20f, 17f)
                    addCircle(4.5f, 7f, 0.9f, Path.Direction.CW)
                    addCircle(4.5f, 12f, 0.9f, Path.Direction.CW)
                    addCircle(4.5f, 17f, 0.9f, Path.Direction.CW)
                }
            "layers" ->
                p {
                    moveTo(12f, 4f)
                    lineTo(21f, 9f)
                    lineTo(12f, 14f)
                    lineTo(3f, 9f)
                    close()
                    moveTo(3f, 13f)
                    lineTo(12f, 18f)
                    lineTo(21f, 13f)
                }
            "bolt" ->
                p {
                    moveTo(13f, 3f)
                    lineTo(5.5f, 13.5f)
                    lineTo(11.5f, 13.5f)
                    lineTo(10.5f, 21f)
                    lineTo(18.5f, 10f)
                    lineTo(12.5f, 10f)
                    close()
                }
            "link" ->
                p {
                    moveTo(10f, 14f)
                    cubicTo(8f, 12f, 8f, 10f, 10f, 8f)
                    lineTo(12.5f, 5.5f)
                    cubicTo(14.5f, 3.5f, 17.5f, 4f, 18.5f, 6f)
                    moveTo(14f, 10f)
                    cubicTo(16f, 12f, 16f, 14f, 14f, 16f)
                    lineTo(11.5f, 18.5f)
                    cubicTo(9.5f, 20.5f, 6.5f, 20f, 5.5f, 18f)
                }
            "home" ->
                p {
                    moveTo(4f, 11f)
                    lineTo(12f, 4f)
                    lineTo(20f, 11f)
                    moveTo(6f, 10f)
                    lineTo(6f, 20f)
                    lineTo(18f, 20f)
                    lineTo(18f, 10f)
                }
            "sliders" ->
                p {
                    moveTo(4f, 8f)
                    lineTo(20f, 8f)
                    moveTo(4f, 16f)
                    lineTo(20f, 16f)
                    addCircle(9f, 8f, 2.3f, Path.Direction.CW)
                    addCircle(15f, 16f, 2.3f, Path.Direction.CW)
                }
            "undo" ->
                p {
                    moveTo(9f, 6f)
                    lineTo(4.5f, 10.5f)
                    lineTo(9f, 15f)
                    moveTo(5f, 10.5f)
                    lineTo(14f, 10.5f)
                    cubicTo(18f, 10.5f, 19.5f, 13f, 19.5f, 15.5f)
                    cubicTo(19.5f, 18f, 17.5f, 19.5f, 15f, 19.5f)
                    lineTo(11f, 19.5f)
                }
            "play" ->
                p {
                    moveTo(8f, 5f)
                    lineTo(19f, 12f)
                    lineTo(8f, 19f)
                    close()
                }
            "pause" ->
                p {
                    addRoundRect(RectF(6.5f, 5f, 10f, 19f), 1f, 1f, Path.Direction.CW)
                    addRoundRect(RectF(14f, 5f, 17.5f, 19f), 1f, 1f, Path.Direction.CW)
                }
            "stop" -> p { addRoundRect(RectF(6f, 6f, 18f, 18f), 2.5f, 2.5f, Path.Direction.CW) }
            "up" ->
                p {
                    moveTo(12f, 19f)
                    lineTo(12f, 5f)
                    moveTo(6f, 11f)
                    lineTo(12f, 5f)
                    lineTo(18f, 11f)
                }
            "down" ->
                p {
                    moveTo(12f, 5f)
                    lineTo(12f, 19f)
                    moveTo(6f, 13f)
                    lineTo(12f, 19f)
                    lineTo(18f, 13f)
                }
            "shield" ->
                p {
                    moveTo(12f, 3.5f)
                    lineTo(19f, 6.5f)
                    lineTo(19f, 12f)
                    cubicTo(19f, 16.5f, 15.5f, 19.5f, 12f, 20.8f)
                    cubicTo(8.5f, 19.5f, 5f, 16.5f, 5f, 12f)
                    lineTo(5f, 6.5f)
                    close()
                }
            "globe" ->
                p {
                    addCircle(12f, 12f, 9f, Path.Direction.CW)
                    moveTo(3f, 12f)
                    lineTo(21f, 12f)
                    addOval(RectF(8f, 3f, 16f, 21f), Path.Direction.CW)
                }
            "bulb" ->
                p {
                    moveTo(9f, 17f)
                    lineTo(15f, 17f)
                    moveTo(10f, 20f)
                    lineTo(14f, 20f)
                    moveTo(9f, 17f)
                    cubicTo(9f, 14.5f, 6.5f, 13.5f, 6.5f, 10f)
                    cubicTo(6.5f, 6.8f, 9f, 4.5f, 12f, 4.5f)
                    cubicTo(15f, 4.5f, 17.5f, 6.8f, 17.5f, 10f)
                    cubicTo(17.5f, 13.5f, 15f, 14.5f, 15f, 17f)
                }
            "note" ->
                p {
                    addRoundRect(RectF(5f, 3.5f, 19f, 20.5f), 3f, 3f, Path.Direction.CW)
                    moveTo(8.5f, 9f)
                    lineTo(15.5f, 9f)
                    moveTo(8.5f, 12.5f)
                    lineTo(15.5f, 12.5f)
                    moveTo(8.5f, 16f)
                    lineTo(12.5f, 16f)
                }
            "user" ->
                p {
                    addCircle(12f, 8.5f, 3.8f, Path.Direction.CW)
                    moveTo(4.5f, 20f)
                    cubicTo(4.5f, 15.5f, 8f, 14f, 12f, 14f)
                    cubicTo(16f, 14f, 19.5f, 15.5f, 19.5f, 20f)
                }
            "image" ->
                p {
                    addRoundRect(RectF(3.5f, 5f, 20.5f, 19f), 3f, 3f, Path.Direction.CW)
                    addCircle(9f, 10f, 1.6f, Path.Direction.CW)
                    moveTo(4f, 17f)
                    lineTo(10f, 12.5f)
                    lineTo(14f, 16f)
                    lineTo(16.5f, 14f)
                    lineTo(20f, 17f)
                }
            "download" ->
                p {
                    moveTo(12f, 4f)
                    lineTo(12f, 15f)
                    moveTo(7f, 10f)
                    lineTo(12f, 15f)
                    lineTo(17f, 10f)
                    moveTo(5f, 19.5f)
                    lineTo(19f, 19.5f)
                }
            "upload" ->
                p {
                    moveTo(12f, 15f)
                    lineTo(12f, 4f)
                    moveTo(7f, 9f)
                    lineTo(12f, 4f)
                    lineTo(17f, 9f)
                    moveTo(5f, 19.5f)
                    lineTo(19f, 19.5f)
                }
            "eye" ->
                p {
                    moveTo(2.5f, 12f)
                    cubicTo(5f, 7.5f, 8.5f, 6f, 12f, 6f)
                    cubicTo(15.5f, 6f, 19f, 7.5f, 21.5f, 12f)
                    cubicTo(19f, 16.5f, 15.5f, 18f, 12f, 18f)
                    cubicTo(8.5f, 18f, 5f, 16.5f, 2.5f, 12f)
                    close()
                    addCircle(12f, 12f, 3f, Path.Direction.CW)
                }
            "eyeoff" ->
                p {
                    moveTo(2.5f, 12f)
                    cubicTo(5f, 7.5f, 8.5f, 6f, 12f, 6f)
                    cubicTo(15.5f, 6f, 19f, 7.5f, 21.5f, 12f)
                    cubicTo(19f, 16.5f, 15.5f, 18f, 12f, 18f)
                    cubicTo(8.5f, 18f, 5f, 16.5f, 2.5f, 12f)
                    close()
                    moveTo(4f, 20f)
                    lineTo(20f, 4f)
                }
            "apps" ->
                p {
                    addRoundRect(RectF(4f, 4f, 10.5f, 10.5f), 2f, 2f, Path.Direction.CW)
                    addRoundRect(RectF(13.5f, 4f, 20f, 10.5f), 2f, 2f, Path.Direction.CW)
                    addRoundRect(RectF(4f, 13.5f, 10.5f, 20f), 2f, 2f, Path.Direction.CW)
                    addRoundRect(RectF(13.5f, 13.5f, 20f, 20f), 2f, 2f, Path.Direction.CW)
                }
            "pin" ->
                p {
                    moveTo(12f, 21f)
                    lineTo(12f, 14f)
                    moveTo(8f, 4f)
                    lineTo(16f, 4f)
                    lineTo(15f, 10f)
                    lineTo(18f, 14f)
                    lineTo(6f, 14f)
                    lineTo(9f, 10f)
                    close()
                }
            "wallpaper" ->
                p {
                    addRoundRect(RectF(3.5f, 4.5f, 20.5f, 19.5f), 3f, 3f, Path.Direction.CW)
                    moveTo(3.5f, 15f)
                    cubicTo(8f, 10f, 11f, 16f, 20.5f, 9.5f)
                }
            "backup" ->
                p {
                    moveTo(7f, 17.5f)
                    cubicTo(4f, 17.5f, 3f, 14f, 6f, 12.8f)
                    cubicTo(6f, 9f, 11f, 7.5f, 13f, 10.5f)
                    cubicTo(16f, 9f, 19f, 11.5f, 17.5f, 14.3f)
                    cubicTo(20.5f, 14.5f, 20.5f, 17.5f, 17.5f, 17.5f)
                    moveTo(12f, 19f)
                    lineTo(12f, 12f)
                    moveTo(9.5f, 14f)
                    lineTo(12f, 11.5f)
                    lineTo(14.5f, 14f)
                }
            "ai" ->
                p {
                    moveTo(12f, 3f)
                    cubicTo(12.6f, 8f, 14f, 10.4f, 20f, 12f)
                    cubicTo(14f, 13.6f, 12.6f, 16f, 12f, 21f)
                    cubicTo(11.4f, 16f, 10f, 13.6f, 4f, 12f)
                    cubicTo(10f, 10.4f, 11.4f, 8f, 12f, 3f)
                    close()
                    moveTo(19f, 3.5f)
                    lineTo(19f, 7f)
                    moveTo(17.2f, 5.2f)
                    lineTo(20.8f, 5.2f)
                }
            "wifi" ->
                p {
                    moveTo(3f, 9.5f)
                    cubicTo(8f, 5f, 16f, 5f, 21f, 9.5f)
                    moveTo(6f, 13f)
                    cubicTo(9.5f, 10f, 14.5f, 10f, 18f, 13f)
                    moveTo(9f, 16.5f)
                    cubicTo(11f, 15f, 13f, 15f, 15f, 16.5f)
                    addCircle(12f, 19.5f, 0.8f, Path.Direction.CW)
                }
            "keyboard" ->
                p {
                    addRoundRect(RectF(3f, 6.5f, 21f, 17.5f), 2.5f, 2.5f, Path.Direction.CW)
                    moveTo(7f, 10f)
                    lineTo(7.5f, 10f)
                    moveTo(11.5f, 10f)
                    lineTo(12.5f, 10f)
                    moveTo(16.5f, 10f)
                    lineTo(17f, 10f)
                    moveTo(8f, 14f)
                    lineTo(16f, 14f)
                }
            "info" ->
                p {
                    addCircle(12f, 12f, 9f, Path.Direction.CW)
                    moveTo(12f, 11f)
                    lineTo(12f, 16.5f)
                    moveTo(12f, 7.6f)
                    lineTo(12f, 7.7f)
                }
            "warn" ->
                p {
                    moveTo(12f, 4f)
                    lineTo(21f, 19.5f)
                    lineTo(3f, 19.5f)
                    close()
                    moveTo(12f, 10f)
                    lineTo(12f, 14.5f)
                    moveTo(12f, 17f)
                    lineTo(12f, 17.1f)
                }
            "move" ->
                p {
                    moveTo(12f, 3f)
                    lineTo(12f, 21f)
                    moveTo(3f, 12f)
                    lineTo(21f, 12f)
                    moveTo(9f, 6f)
                    lineTo(12f, 3f)
                    lineTo(15f, 6f)
                    moveTo(9f, 18f)
                    lineTo(12f, 21f)
                    lineTo(15f, 18f)
                }
            "float" ->
                p {
                    addRoundRect(RectF(3.5f, 5f, 20.5f, 19f), 3f, 3f, Path.Direction.CW)
                    addRoundRect(RectF(11f, 11f, 19f, 17.5f), 1.8f, 1.8f, Path.Direction.CW)
                }
            "split" ->
                p {
                    addRoundRect(RectF(3.5f, 4.5f, 20.5f, 19.5f), 3f, 3f, Path.Direction.CW)
                    moveTo(12f, 4.5f)
                    lineTo(12f, 19.5f)
                }
            else -> p { addCircle(12f, 12f, 8f, Path.Direction.CW) }
        }
}

class IconView(c: Context, var name: String, var color: Int, sizeDp: Int = 20, var strokeW: Float = 1.8f) : View(c) {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    var sizePx = T.dp(sizeDp)
    var filled = false

    override fun onMeasure(
        w: Int,
        h: Int,
    ) = setMeasuredDimension(sizePx, sizePx)

    fun set(
        n: String,
        col: Int = color,
    ) {
        name = n
        color = col
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = width / 24f
        c.save()
        c.scale(s, s)
        paint.color = color
        paint.strokeWidth = strokeW
        paint.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        c.drawPath(Icons.path(name), paint)
        c.restore()
    }
}
