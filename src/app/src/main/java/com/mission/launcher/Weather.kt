package com.mission.launcher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WeatherSnapshot(
    val city: String,
    val code: Int,
    val temperatureC: Double,
    val humidity: Int,
    val fetchedAt: Long,
    val cloudCover: Int = 0,
    val isDay: Boolean = true,
    val visibilityM: Double = 10000.0,
    val windSpeedKmh: Double = 0.0,
    val precipitationMm: Double = 0.0,
    val sunriseEpoch: Long = 0L,
    val sunsetEpoch: Long = 0L,
)

object Weather {
    enum class Scene { CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, RAIN, HEAVY_RAIN, THUNDERSTORM, SNOW, HAZE }

    fun mapWeatherCode(
        code: Int,
        visibilityM: Double = 10000.0,
    ): Scene =
        when {
            visibilityM < 1000.0 -> Scene.FOG
            code == 45 || code == 48 -> Scene.FOG
            code == 0 -> Scene.CLEAR
            code == 1 || code == 2 -> Scene.PARTLY_CLOUDY
            code == 3 -> Scene.OVERCAST
            code in 51..57 -> Scene.DRIZZLE
            code in 61..67 || code in 80..82 -> if (code in 65..67 || code in 81..82) Scene.HEAVY_RAIN else Scene.RAIN
            code in 95..99 -> Scene.THUNDERSTORM
            code in 71..77 || code == 85 || code == 86 -> Scene.SNOW
            visibilityM < 5000.0 -> Scene.HAZE
            else -> Scene.CLEAR
        }

    data class City(val id: String, val label: String, val latitude: Double, val longitude: Double)

    val cities =
        listOf(
            City("prayagraj", "Prayagraj, Uttar Pradesh", 25.4358, 81.8463),
            City("lucknow", "Lucknow, Uttar Pradesh", 26.8467, 80.9462),
            City("delhi", "New Delhi", 28.6139, 77.2090),
            City("mumbai", "Mumbai", 19.0760, 72.8777),
            City("kolkata", "Kolkata", 22.5726, 88.3639),
            City("chennai", "Chennai", 13.0827, 80.2707),
            City("bengaluru", "Bengaluru", 12.9716, 77.5946),
        )

    private const val CACHE_KEY = "cache_weather"
    private const val REFRESH_AFTER_MS = 3 * 60 * 60 * 1000L
    private const val MIN_REQUEST_GAP_MS = 60 * 1000L

    fun enabled() = Store.b("wp_weather", true)

    fun city(): City = cities.firstOrNull { it.id == Store.s("wp_weather_city", "prayagraj") } ?: cities.first()

    fun label(id: String = city().id) = cities.firstOrNull { it.id == id }?.label ?: cities.first().label

    fun condition(code: Int): String =
        when (code) {
            0 -> "Clear"
            1 -> "Mostly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            in 51..57 -> "Drizzle"
            in 61..67, in 80..82 -> "Rain"
            in 71..77, 85, 86 -> "Snow"
            in 95..99 -> "Thunderstorm"
            else -> "Weather condition $code"
        }

    fun cached(): WeatherSnapshot? {
        val o = Store.obj(CACHE_KEY)
        val c = city()
        if (o.optString("city") != c.id || !o.has("fetched_at")) return null
        val temperature = o.optDouble("temperature_c", Double.NaN)
        val code = o.optInt("code", -1)
        val humidity = o.optInt("humidity", -1)
        val fetchedAt = o.optLong("fetched_at", 0L)
        val cloudCover = o.optInt("cloud_cover", 0).coerceIn(0, 100)
        val isDay = o.optBoolean("is_day", true)
        val visibilityM = o.optDouble("visibility", 10000.0)
        val windSpeedKmh = o.optDouble("wind_speed", 0.0)
        val precipitationMm = o.optDouble("precipitation", 0.0)
        val sunriseEpoch = o.optLong("sunrise_epoch", 0L)
        val sunsetEpoch = o.optLong("sunset_epoch", 0L)
        if (!temperature.isFinite() || code !in 0..99 || humidity !in 0..100 || fetchedAt <= 0L) return null
        return WeatherSnapshot(
            c.id, code, temperature, humidity, fetchedAt, cloudCover, isDay,
            visibilityM, windSpeedKmh, precipitationMm, sunriseEpoch, sunsetEpoch,
        )
    }

    fun state(now: Long = System.currentTimeMillis()): String {
        if (!enabled()) return "Weather effects off"
        val data = cached() ?: return if (Store.s("wp_weather_error").isNotBlank()) "Weather unavailable" else "Waiting for first update"
        val age = now - data.fetchedAt
        return when {
            age < 0L -> "Weather time invalid"
            age <= REFRESH_AFTER_MS -> "Updated · ${label(data.city)}"
            Store.s("wp_weather_error").isNotBlank() -> "Stale · ${label(data.city)} · refresh failed"
            else -> "Stale cache · ${label(data.city)}"
        }
    }

    fun lastFetchedLabel(): String {
        val data = cached() ?: return "No successful update yet"
        val time =
            DateTimeFormatter.ofPattern(
                "dd MMM, HH:mm",
                Locale.ENGLISH,
            ).withZone(Repo.zone()).format(Instant.ofEpochMilli(data.fetchedAt))
        return String.format(Locale.US, "Last fetched %s IST · %s · %.1f°C", time, condition(data.code), data.temperatureC)
    }

    fun forRendering(now: Long = System.currentTimeMillis()): WeatherSnapshot? {
        if (!enabled()) return null
        val data = cached() ?: return null
        val age = now - data.fetchedAt
        return data.takeIf { age in 0 until REFRESH_AFTER_MS }
    }

    /** Refreshes only on wallpaper work, never from a UI render; returns cached data on network failure. */
    @Synchronized fun refreshIfNeeded(
        context: Context,
        now: Long = System.currentTimeMillis(),
        force: Boolean = false,
    ): WeatherSnapshot? {
        if (!enabled()) return null
        val previous = cached()
        if (!force && previous != null && now - previous.fetchedAt in 0 until REFRESH_AFTER_MS) return previous
        val attemptKey = "wp_weather_attempt_${city().id}"
        if (force && now - Store.l(attemptKey, 0L) in 0 until MIN_REQUEST_GAP_MS) return previous
        if (!context.online()) {
            Store.put("wp_weather_error", "No internet connection")
            return previous
        }
        Store.put(attemptKey, now)

        return try {
            val c = city()
            val query =
                "latitude=${c.latitude}&longitude=${c.longitude}" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,cloud_cover,is_day,visibility,wind_speed_10m,precipitation" +
                    "&daily=sunrise,sunset" +
                    "&timezone=${URLEncoder.encode("Asia/Kolkata", "UTF-8")}"
            val connection = URL("https://api.open-meteo.com/v1/forecast?$query").openConnection() as HttpURLConnection
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.setRequestProperty("Accept", "application/json")
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) throw IllegalStateException("Weather provider HTTP $responseCode")
                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val current =
                    JSONObject(payload).optJSONObject("current")
                        ?: throw IllegalStateException("Weather response is missing current conditions")
                val temperature = current.optDouble("temperature_2m", Double.NaN)
                val humidity = current.optInt("relative_humidity_2m", -1)
                val code = current.optInt("weather_code", -1)
                val cloudCover = current.optInt("cloud_cover", 0).coerceIn(0, 100)
                val isDay = current.optInt("is_day", 1) == 1
                val visibilityM = current.optDouble("visibility", 10000.0).coerceIn(0.0, 100000.0)
                val windSpeed = current.optDouble("wind_speed_10m", 0.0).coerceAtLeast(0.0)
                val precipitation = current.optDouble("precipitation", 0.0).coerceAtLeast(0.0)
                val daily = JSONObject(payload).optJSONObject("daily")
                val sunriseText = daily?.optJSONArray("sunrise")?.optString(0, "")
                val sunsetText = daily?.optJSONArray("sunset")?.optString(0, "")
                val sunriseEpoch =
                    if (sunriseText.isNullOrBlank()) {
                        0L
                    } else {
                        try {
                            java.time.LocalDateTime.parse(sunriseText, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                .atZone(Repo.zone()).toInstant().toEpochMilli()
                        } catch (_: Exception) {
                            0L
                        }
                    }
                val sunsetEpoch =
                    if (sunsetText.isNullOrBlank()) {
                        0L
                    } else {
                        try {
                            java.time.LocalDateTime.parse(sunsetText, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                .atZone(Repo.zone()).toInstant().toEpochMilli()
                        } catch (_: Exception) {
                            0L
                        }
                    }
                if (!temperature.isFinite() || temperature !in -90.0..65.0 || humidity !in 0..100 || code !in 0..99) {
                    throw IllegalStateException("Weather response contains invalid conditions")
                }
                val result =
                    WeatherSnapshot(
                        c.id, code, temperature, humidity, now, cloudCover, isDay,
                        visibilityM, windSpeed, precipitation, sunriseEpoch, sunsetEpoch,
                    )
                Store.saveObj(
                    CACHE_KEY,
                    JSONObject()
                        .put("city", result.city)
                        .put("code", result.code)
                        .put("temperature_c", result.temperatureC)
                        .put("humidity", result.humidity)
                        .put("fetched_at", result.fetchedAt)
                        .put("cloud_cover", result.cloudCover)
                        .put("is_day", result.isDay)
                        .put("visibility", result.visibilityM)
                        .put("wind_speed", result.windSpeedKmh)
                        .put("precipitation", result.precipitationMm)
                        .put("sunrise_epoch", result.sunriseEpoch)
                        .put("sunset_epoch", result.sunsetEpoch),
                )
                val saved = Store.obj(CACHE_KEY)
                if (saved.optString("city") != result.city ||
                    saved.optInt("code", -1) != result.code ||
                    saved.optLong("fetched_at", 0L) != result.fetchedAt
                ) {
                    throw IllegalStateException("Could not persist weather cache")
                }
                Store.put("wp_weather_error", "")
                result
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            Store.put("wp_weather_error", message.take(160))
            Log.e("weather-refresh", e)
            previous
        }
    }
}
