package com.mission.launcher

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.asin

object MoonMath {
    private const val SYNODIC_MONTH = 29.530588853

    /** Fraction of the lunar cycle from 0 (new) to 1 (new). */
    fun phase(date: LocalDate): Double {
        val julianDay = date.toEpochDay() + 2440588.0
        val raw = (julianDay - 2451550.1) % SYNODIC_MONTH
        val normalized = if (raw < 0) raw + SYNODIC_MONTH else raw
        return normalized / SYNODIC_MONTH
    }

    fun illumination(date: LocalDate): Double = (1.0 - cos(phase(date) * 2.0 * PI)) * 0.5

    fun solarAltitude(date: LocalDate, istHour: Double, latitude: Double = 25.4358, longitude: Double = 81.8463): Double {
        val n = date.dayOfYear
        val gamma = 2.0 * PI / 365.0 * (n - 1)
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )
        val declination = (
            0.006918 -
                0.399912 * cos(gamma) +
                0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) +
                0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) +
                0.00148 * sin(3 * gamma)
            )
        val localMinutes = istHour * 60.0
        val timezoneOffsetMinutes = 330.0
        val trueSolarMinutes = localMinutes + eqTime + 4.0 * longitude - timezoneOffsetMinutes
        val hourAngle = PI / 180.0 * (trueSolarMinutes / 4.0 - 180.0)
        val lat = latitude * PI / 180.0
        val altitude = asin(sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle))
        return altitude * 180.0 / PI
    }

    fun twilightBand(date: LocalDate, istHour: Double): String {
        val altitude = solarAltitude(date, istHour)
        return when {
            altitude >= -6.0 -> "civil"
            altitude >= -12.0 -> "nautical"
            altitude >= -18.0 -> "astronomical"
            else -> "night"
        }
    }
}
