package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.simulation.QualityMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.seaDataStore by preferencesDataStore(name = "sea_and_sky_prefs")

data class SavedPreferences(
    val autoMode: Boolean = false,
    val lat: Double = 55.75,
    val lon: Double = 37.62,
    val qualityMode: QualityMode = QualityMode.AUTO,
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val AUTO_MODE = booleanPreferencesKey("sea_auto")
        val LAT = doublePreferencesKey("sea_lat")
        val LON = doublePreferencesKey("sea_lon")
        val QUALITY = stringPreferencesKey("sea_quality")
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
}
