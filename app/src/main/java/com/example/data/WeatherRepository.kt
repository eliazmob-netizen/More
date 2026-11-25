package com.example.data

import com.example.simulation.AtmosphereMath
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.pow

data class WeatherSnapshot(
    val utcOffsetSeconds: Int?,
    val wind: Double,
    val cloud: Double,
    val cloudLow: Double,
    val cloudMid: Double,
    val cloudHigh: Double,
    val rain: Double,
    val fog: Double,
    val storm: Double,
    val windDir: Double,
    val windSpeedKmh: Double,
    val cloudCoverPercent: Double,
    val temperatureC: Double? = null,
    val weatherCode: Int = 0,
    val precipitationMm: Double = 0.0,
)

class WeatherRepository {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(9, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .writeTimeout(9, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().build()

    private val forecastApi: OpenMeteoForecastApi = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoForecastApi::class.java)

    private val geocodingApi: OpenMeteoGeocodingApi = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoGeocodingApi::class.java)

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherSnapshot {
        val response = forecastApi.getCurrentWeather(latitude = lat, longitude = lon)
        val c = response.current ?: error("Empty current weather payload")
        val w = c.weatherCode
        val wind = AtmosphereMath.clamp((c.windSpeed10m / 55.0).pow(0.8), 0.0, 1.0)
        val cloud = AtmosphereMath.clamp(c.cloudCover / 100.0, 0.0, 1.0)
        val cloudLow = AtmosphereMath.clamp((c.cloudCoverLow ?: (c.cloudCover * 0.75)) / 100.0, 0.0, 1.0)
        val cloudMid = AtmosphereMath.clamp((c.cloudCoverMid ?: (c.cloudCover * 0.85)) / 100.0, 0.0, 1.0)
        val cloudHigh = AtmosphereMath.clamp((c.cloudCoverHigh ?: (c.cloudCover * 0.65)) / 100.0, 0.0, 1.0)
        val rawRain = when {
            (w in 51..67) || (w in 80..82) -> 0.3 + c.precipitation / 3.0
            c.precipitation > 0.0 -> c.precipitation / 4.0
            else -> 0.0
        }
        var rain = AtmosphereMath.clamp(rawRain, 0.0, 1.0)
        val fog = if (w == 45 || w == 48) 0.8 else 0.0
        val storm = if (w >= 95) 1.0 else 0.0
        if (storm > 0.5) rain = max(rain, 0.85)

        return WeatherSnapshot(
            utcOffsetSeconds = response.utcOffsetSeconds,
            wind = wind,
            cloud = cloud,
            cloudLow = cloudLow,
            cloudMid = cloudMid,
            cloudHigh = cloudHigh,
            rain = rain,
            fog = fog,
            storm = storm,
            windDir = (c.windDirection10m + 180.0) % 360.0,
            windSpeedKmh = c.windSpeed10m,
            cloudCoverPercent = c.cloudCover,
            temperatureC = c.temperature2m,
            weatherCode = w,
            precipitationMm = c.precipitation,
        )
    }

    suspend fun findCity(query: String): GeocodingResult? {
        val response = geocodingApi.searchCity(name = query.trim())
        return response.results?.firstOrNull()
    }
}
