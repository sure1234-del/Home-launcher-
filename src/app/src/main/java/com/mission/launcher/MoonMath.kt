package com.mission.launcher

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos

object MoonMath {
    private const val SYNODIC_MONTH = 29.530588853

    /** Fraction of the lunar cycle from 0 (new) to 1 (new). */
    fun phase(date: LocalDate): Double {
        val julianDay = date.toEpochDay() + 2440588.0
        val raw = (julianDay - 2451550.1) % SYNODIC_MONTH
        val normalized = if (raw < 0) raw + SYNODIC_MONTH else raw
        return normalized / SYNODIC_MONTH
    }

    fun illumination(date: LocalDate): Double =
        (1.0 - cos(phase(date) * 2.0 * PI)) * 0.5
}
