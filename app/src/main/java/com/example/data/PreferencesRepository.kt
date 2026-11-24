package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.simulation.QualityMode
import com.example.simulation.SeaState
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
    val rain: Double = 0.0,
    val fog: Double = 0.0,
    val storm: Double = 0.0,
    val cam: Double = 250.0,
    val windDir: Double = 50.0,
    val realTime: Boolean = true,
    val fastDay: Boolean = false,
    val manualTimeHours: Double = 12.0,
    val lhOn: Boolean = true,
    val shipOn: Boolean = true,
    val birdsOn: Boolean = true,
    val dropsOn: Boolean = true,
    val fastShip: Boolean = false,
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
        val RAIN = doublePreferencesKey("sea_rain")
        val FOG = doublePreferencesKey("sea_fog")
        val STORM = doublePreferencesKey("sea_storm")
        val CAM = doublePreferencesKey("sea_cam")
        val WIND_DIR = doublePreferencesKey("sea_wind_dir")
        val REAL_TIME = booleanPreferencesKey("sea_real_time")
        val FAST_DAY = booleanPreferencesKey("sea_fast_day")
        val MANUAL_TIME = doublePreferencesKey("sea_manual_time")
        val LH_ON = booleanPreferencesKey("sea_lh_on")
        val SHIP_ON = booleanPreferencesKey("sea_ship_on")
        val BIRDS_ON = booleanPreferencesKey("sea_birds_on")
        val DROPS_ON = booleanPreferencesKey("sea_drops_on")
        val FAST_SHIP = booleanPreferencesKey("sea_fast_ship")
    }

    val preferencesFlow: Flow<SavedPreferences> = context.seaDataStore.data.map { prefs ->
        val quality = prefs[Keys.QUALITY]?.let { name ->
            QualityMode.entries.firstOrNull { it.name == name }
        } ?: QualityMode.AUTO
        SavedPreferences(
            autoMode = prefs[Keys.AUTO_MODE] ?: false,
            lat = prefs[Keys.LAT] ?: 55.75,
            lon = prefs[Keys.LON] ?: 37.62,
            qualityMode = quality,
            gyroMode = prefs[Keys.GYRO_MODE] ?: false,
            wind = prefs[Keys.WIND] ?: 0.22,
            cloud = prefs[Keys.CLOUD] ?: 0.30,
            rain = prefs[Keys.RAIN] ?: 0.0,
            fog = prefs[Keys.FOG] ?: 0.0,
            storm = prefs[Keys.STORM] ?: 0.0,
            cam = prefs[Keys.CAM] ?: 250.0,
            windDir = prefs[Keys.WIND_DIR] ?: 50.0,
            realTime = prefs[Keys.REAL_TIME] ?: true,
            fastDay = prefs[Keys.FAST_DAY] ?: false,
            manualTimeHours = prefs[Keys.MANUAL_TIME] ?: 12.0,
            lhOn = prefs[Keys.LH_ON] ?: true,
            shipOn = prefs[Keys.SHIP_ON] ?: true,
            birdsOn = prefs[Keys.BIRDS_ON] ?: true,
            dropsOn = prefs[Keys.DROPS_ON] ?: true,
            fastShip = prefs[Keys.FAST_SHIP] ?: false,
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

    suspend fun saveSeaState(state: SeaState) {
        context.seaDataStore.edit { prefs ->
            prefs[Keys.AUTO_MODE] = state.autoMode
            prefs[Keys.LAT] = state.lat
            prefs[Keys.LON] = state.lon
            prefs[Keys.QUALITY] = state.qualityMode.name
            prefs[Keys.GYRO_MODE] = state.gyroMode
            prefs[Keys.WIND] = state.wind
            prefs[Keys.CLOUD] = state.cloud
            prefs[Keys.RAIN] = state.rain
            prefs[Keys.FOG] = state.fog
            prefs[Keys.STORM] = state.storm
            prefs[Keys.CAM] = state.cam
            prefs[Keys.WIND_DIR] = state.windDir
            prefs[Keys.REAL_TIME] = state.realTime
            prefs[Keys.FAST_DAY] = state.fastDay
            prefs[Keys.MANUAL_TIME] = state.t
            prefs[Keys.LH_ON] = state.lhOn
            prefs[Keys.SHIP_ON] = state.shipOn
            prefs[Keys.BIRDS_ON] = state.birdsOn
            prefs[Keys.DROPS_ON] = state.dropsOn
            prefs[Keys.FAST_SHIP] = state.fastShip
        }
    }
}
