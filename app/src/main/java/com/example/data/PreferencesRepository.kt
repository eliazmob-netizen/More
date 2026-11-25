package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.simulation.ColorProfile
import com.example.simulation.LighthouseMode
import com.example.simulation.QualityMode
import com.example.simulation.SeaState
import com.example.simulation.WallpaperScrollMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.seaDataStore by preferencesDataStore(name = "sea_and_sky_prefs")

data class SavedPreferences(
    val autoMode: Boolean = false,
    val lat: Double = 55.75,
    val lon: Double = 37.62,
    val qualityMode: QualityMode = QualityMode.AUTO,
    val gyroMode: Boolean = false,
    val wind: Double = 0.22,
    val cloud: Double = 0.30,
    val cloudLow: Double = 0.25,
    val cloudMid: Double = 0.28,
    val cloudHigh: Double = 0.22,
    val rain: Double = 0.0,
    val fog: Double = 0.0,
    val storm: Double = 0.0,
    val cam: Double = 250.0,
    val camPitch: Double = 0.0,
    val windDir: Double = 50.0,
    val realTime: Boolean = true,
    val fastDay: Boolean = false,
    val manualTimeHours: Double = 12.0,
    val lhOn: Boolean = true,
    val lhMode: LighthouseMode = LighthouseMode.FAST,
    val shipOn: Boolean = true,
    val birdsOn: Boolean = true,
    val dropsOn: Boolean = true,
    val fastShip: Boolean = false,
    val scrollMode: WallpaperScrollMode = WallpaperScrollMode.FIXED,
    val screenCount: Int = 3,
    val homeScreenIndex: Int = 1,
    val scrollSpanDeg: Double = 90.0,
    val scrollReverse: Boolean = false,
    val detectedScreenCount: Int = 0,
    val colorProfile: ColorProfile = ColorProfile.VIVID,
    val showWeatherHud: Boolean = true,
    val weatherHudTopPct: Double = 0.14,
    val cityName: String = "",
    val temperatureC: Double? = null,
    val weatherCode: Int = -1,
    val precipitationMm: Double = 0.0,
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val AUTO_MODE = booleanPreferencesKey("sea_auto")
        val LAT = doublePreferencesKey("sea_lat")
        val LON = doublePreferencesKey("sea_lon")
        val QUALITY = stringPreferencesKey("sea_quality")
        val GYRO_MODE = booleanPreferencesKey("sea_gyro")
        val WIND = doublePreferencesKey("sea_wind")
        val CLOUD = doublePreferencesKey("sea_cloud")
        val CLOUD_LOW = doublePreferencesKey("sea_cloud_low")
        val CLOUD_MID = doublePreferencesKey("sea_cloud_mid")
        val CLOUD_HIGH = doublePreferencesKey("sea_cloud_high")
        val RAIN = doublePreferencesKey("sea_rain")
        val FOG = doublePreferencesKey("sea_fog")
        val STORM = doublePreferencesKey("sea_storm")
        val CAM = doublePreferencesKey("sea_cam")
        val CAM_PITCH = doublePreferencesKey("sea_cam_pitch")
        val WIND_DIR = doublePreferencesKey("sea_wind_dir")
        val REAL_TIME = booleanPreferencesKey("sea_real_time")
        val FAST_DAY = booleanPreferencesKey("sea_fast_day")
        val MANUAL_TIME = doublePreferencesKey("sea_manual_time")
        val LH_ON = booleanPreferencesKey("sea_lh_on")
        val LH_MODE = stringPreferencesKey("sea_lh_mode")
        val SHIP_ON = booleanPreferencesKey("sea_ship_on")
        val BIRDS_ON = booleanPreferencesKey("sea_birds_on")
        val DROPS_ON = booleanPreferencesKey("sea_drops_on")
        val FAST_SHIP = booleanPreferencesKey("sea_fast_ship")
        val SCROLL_MODE = stringPreferencesKey("sea_scroll_mode")
        val SCREEN_COUNT = intPreferencesKey("sea_screen_count")
        val HOME_SCREEN_INDEX = intPreferencesKey("sea_home_screen_index")
        val SCROLL_SPAN_DEG = doublePreferencesKey("sea_scroll_span_deg")
        val SCROLL_REVERSE = booleanPreferencesKey("sea_scroll_reverse")
        val DETECTED_SCREEN_COUNT = intPreferencesKey("sea_detected_screen_count")
        val COLOR_PROFILE = stringPreferencesKey("sea_color_profile")
        val SHOW_WEATHER_HUD = booleanPreferencesKey("sea_show_weather_hud")
        val WEATHER_HUD_TOP_PCT = doublePreferencesKey("sea_weather_hud_top_pct")
        val CITY_NAME = stringPreferencesKey("sea_city_name")
        val TEMPERATURE_C = doublePreferencesKey("sea_temperature_c")
        val WEATHER_CODE = intPreferencesKey("sea_weather_code")
        val PRECIPITATION_MM = doublePreferencesKey("sea_precipitation_mm")
    }

    val preferencesFlow: Flow<SavedPreferences> = context.seaDataStore.data.map { prefs ->
        val quality = prefs[Keys.QUALITY]?.let { name ->
            QualityMode.entries.firstOrNull { it.name == name }
        } ?: QualityMode.AUTO
        val lhMode = prefs[Keys.LH_MODE]?.let { name ->
            LighthouseMode.entries.firstOrNull { it.name == name }
        } ?: LighthouseMode.FAST
        val scrollMode = prefs[Keys.SCROLL_MODE]?.let { name ->
            WallpaperScrollMode.entries.firstOrNull { it.name == name }
        } ?: WallpaperScrollMode.FIXED
        val colorProfile = prefs[Keys.COLOR_PROFILE]?.let { name ->
            ColorProfile.entries.firstOrNull { it.name == name }
        } ?: ColorProfile.VIVID
        SavedPreferences(
            autoMode = prefs[Keys.AUTO_MODE] ?: false,
            lat = prefs[Keys.LAT] ?: 55.75,
            lon = prefs[Keys.LON] ?: 37.62,
            qualityMode = quality,
            gyroMode = prefs[Keys.GYRO_MODE] ?: false,
            wind = prefs[Keys.WIND] ?: 0.22,
            cloud = prefs[Keys.CLOUD] ?: 0.30,
            cloudLow = prefs[Keys.CLOUD_LOW] ?: 0.25,
            cloudMid = prefs[Keys.CLOUD_MID] ?: 0.28,
            cloudHigh = prefs[Keys.CLOUD_HIGH] ?: 0.22,
            rain = prefs[Keys.RAIN] ?: 0.0,
            fog = prefs[Keys.FOG] ?: 0.0,
            storm = prefs[Keys.STORM] ?: 0.0,
            cam = prefs[Keys.CAM] ?: 250.0,
            camPitch = prefs[Keys.CAM_PITCH] ?: 0.0,
            windDir = prefs[Keys.WIND_DIR] ?: 50.0,
            realTime = prefs[Keys.REAL_TIME] ?: true,
            fastDay = prefs[Keys.FAST_DAY] ?: false,
            manualTimeHours = prefs[Keys.MANUAL_TIME] ?: 12.0,
            lhOn = prefs[Keys.LH_ON] ?: true,
            lhMode = lhMode,
            shipOn = prefs[Keys.SHIP_ON] ?: true,
            birdsOn = prefs[Keys.BIRDS_ON] ?: true,
            dropsOn = prefs[Keys.DROPS_ON] ?: true,
            fastShip = prefs[Keys.FAST_SHIP] ?: false,
            scrollMode = scrollMode,
            screenCount = (prefs[Keys.SCREEN_COUNT] ?: 3).coerceIn(2, 9),
            homeScreenIndex = (prefs[Keys.HOME_SCREEN_INDEX] ?: 1).coerceAtLeast(1),
            scrollSpanDeg = (prefs[Keys.SCROLL_SPAN_DEG] ?: 90.0).coerceIn(20.0, 180.0),
            scrollReverse = prefs[Keys.SCROLL_REVERSE] ?: false,
            detectedScreenCount = (prefs[Keys.DETECTED_SCREEN_COUNT] ?: 0).coerceAtLeast(0),
            colorProfile = colorProfile,
            showWeatherHud = prefs[Keys.SHOW_WEATHER_HUD] ?: true,
            weatherHudTopPct = (prefs[Keys.WEATHER_HUD_TOP_PCT] ?: 0.14).coerceIn(0.02, 0.60),
            cityName = prefs[Keys.CITY_NAME] ?: "",
            temperatureC = prefs[Keys.TEMPERATURE_C],
            weatherCode = prefs[Keys.WEATHER_CODE] ?: -1,
            precipitationMm = prefs[Keys.PRECIPITATION_MM] ?: 0.0,
        )
    }

    suspend fun setAutoMode(enabled: Boolean) {
        context.seaDataStore.edit { it[Keys.AUTO_MODE] = enabled }
    }

    suspend fun setCoordinates(lat: Double, lon: Double) {
        context.seaDataStore.edit {
            it[Keys.LAT] = lat
            it[Keys.LON] = lon
        }
    }

    suspend fun setQualityMode(mode: QualityMode) {
        context.seaDataStore.edit { it[Keys.QUALITY] = mode.name }
    }

    suspend fun setDetectedScreenCount(count: Int) {
        context.seaDataStore.edit { it[Keys.DETECTED_SCREEN_COUNT] = count.coerceIn(1, 12) }
    }

    suspend fun saveSeaState(state: SeaState) {
        context.seaDataStore.edit { prefs ->
            prefs[Keys.AUTO_MODE] = state.autoMode
            prefs[Keys.LAT] = state.lat
            prefs[Keys.LON] = state.lon
            prefs[Keys.QUALITY] = state.qualityMode.name
            prefs[Keys.GYRO_MODE] = state.gyroMode
            prefs[Keys.WIND] = state.wind
            prefs[Keys.CLOUD] = state.cloud
            prefs[Keys.CLOUD_LOW] = state.cloudLow
            prefs[Keys.CLOUD_MID] = state.cloudMid
            prefs[Keys.CLOUD_HIGH] = state.cloudHigh
            prefs[Keys.RAIN] = state.rain
            prefs[Keys.FOG] = state.fog
            prefs[Keys.STORM] = state.storm
            prefs[Keys.CAM] = state.cam
            prefs[Keys.CAM_PITCH] = state.camPitch
            prefs[Keys.WIND_DIR] = state.windDir
            prefs[Keys.REAL_TIME] = state.realTime
            prefs[Keys.FAST_DAY] = state.fastDay
            prefs[Keys.MANUAL_TIME] = state.t
            prefs[Keys.LH_ON] = state.lhOn
            prefs[Keys.LH_MODE] = state.lhMode.name
            prefs[Keys.SHIP_ON] = state.shipOn
            prefs[Keys.BIRDS_ON] = state.birdsOn
            prefs[Keys.DROPS_ON] = state.dropsOn
            prefs[Keys.FAST_SHIP] = state.fastShip
            prefs[Keys.SCROLL_MODE] = state.scrollMode.name
            prefs[Keys.SCREEN_COUNT] = state.screenCount
            prefs[Keys.HOME_SCREEN_INDEX] = state.homeScreenIndex
            prefs[Keys.SCROLL_SPAN_DEG] = state.scrollSpanDeg
            prefs[Keys.SCROLL_REVERSE] = state.scrollReverse
            prefs[Keys.COLOR_PROFILE] = state.colorProfile.name
            prefs[Keys.SHOW_WEATHER_HUD] = state.showWeatherHud
            prefs[Keys.WEATHER_HUD_TOP_PCT] = state.weatherHudTopPct
            prefs[Keys.CITY_NAME] = state.cityName
            if (state.temperatureC != null) {
                prefs[Keys.TEMPERATURE_C] = state.temperatureC
            } else {
                prefs.remove(Keys.TEMPERATURE_C)
            }
            prefs[Keys.WEATHER_CODE] = state.weatherCode
            prefs[Keys.PRECIPITATION_MM] = state.precipitationMm
        }
    }
}
