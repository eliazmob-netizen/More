package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class ForecastResponse(
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @Json(name = "current") val current: CurrentWeather? = null,
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @Json(name = "weather_code") val weatherCode: Int = 0,
    @Json(name = "cloud_cover") val cloudCover: Double = 0.0,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double = 0.0,
    @Json(name = "wind_direction_10m") val windDirection10m: Double = 0.0,
    @Json(name = "precipitation") val precipitation: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    @Json(name = "results") val results: List<GeocodingResult>? = null,
)

@JsonClass(generateAdapter = true)
data class GeocodingResult(
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
)

interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,precipitation",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 1,
    ): ForecastResponse
}

interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "ru",
    ): GeocodingResponse
}
