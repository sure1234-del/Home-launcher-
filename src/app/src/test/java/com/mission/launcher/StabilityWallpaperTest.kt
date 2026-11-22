package com.mission.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StabilityWallpaperTest {
    @Test
    fun istHalfHourBoundaryIsAtNextSlot() {
        val zone = TimeProvider.zone
        val noon = LocalDate.of(2026, 11, 21).atTime(12, 10).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 21).atTime(12, 30).atZone(zone).toInstant().toEpochMilli(),
            TimeProvider.nextHalfHour(noon))
        val pm = LocalDate.of(2026, 11, 21).atTime(12, 45).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 21).atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
            TimeProvider.nextHalfHour(pm))
    }

    @Test
    fun monthAndYearBoundariesStayIst() {
        val zone = TimeProvider.zone
        assertEquals("Asia/Kolkata", zone.id)
        val midnight = LocalDate.of(2026, 12, 31).atTime(23, 45).atZone(zone).toInstant()
        assertEquals(LocalDate.of(2026, 12, 31), Instant.ofEpochMilli(midnight.toEpochMilli()).atZone(zone).toLocalDate())
    }

    @Test
    fun wmoMappingCoversCoreScenes() {
        assertEquals(Weather.Scene.CLEAR, Weather.mapWeatherCode(0))
        assertEquals(Weather.Scene.PARTLY_CLOUDY, Weather.mapWeatherCode(2))
        assertEquals(Weather.Scene.FOG, Weather.mapWeatherCode(45))
        assertEquals(Weather.Scene.DRIZZLE, Weather.mapWeatherCode(51))
        assertEquals(Weather.Scene.RAIN, Weather.mapWeatherCode(61))
        assertEquals(Weather.Scene.THUNDERSTORM, Weather.mapWeatherCode(95))
        assertEquals(Weather.Scene.SNOW, Weather.mapWeatherCode(71))
        assertEquals(Weather.Scene.FOG, Weather.mapWeatherCode(0, 800.0))
    }

    @Test
    fun moonIlluminationIsKnownAtFullMoon() {
        val full = LocalDate.of(2026, 1, 3)
        val illumination = MoonMath.illumination(full)
        assertTrue(illumination in 0.45..1.0)
    }
}
