package com.example.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.LocationHelper
import com.example.data.PreferencesRepository
import com.example.data.WeatherRepository
import com.example.sensors.OrientationController
import com.example.simulation.AtmosphereMath
import com.example.simulation.FpsStats
import com.example.simulation.QualityMode
import com.example.simulation.SeaRenderer
import com.example.simulation.SeaState
import com.example.simulation.TimePreset
import com.example.simulation.WeatherPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SeaUiState(
    val seaState: SeaState = SeaState(),
    val isControlsOpen: Boolean = false,
    val showFps: Boolean = false,
    val fpsStats: FpsStats = FpsStats(),
    val solarElevationDeg: Double = 0.0,
    val cityQuery: String = "",
    val sourceStatus: String = "",
    val statusMessage: String = "",
    val glError: String? = null,
    val activeWeatherPreset: WeatherPreset? = null,
    val isRainbowActive: Boolean = false,
    val activeTimePreset: TimePreset? = null,
    val isSearchingCity: Boolean = false,
)

class SeaViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val weatherRepo = WeatherRepository()
    private val prefsRepo = PreferencesRepository(appContext)
    private val locationHelper = LocationHelper(appContext)

    private var timeRevision = 0L
    private var qualityRevision = 0L
    private var autoJob: Job? = null
    private var persistJob: Job? = null
    private var lastUiTimeSyncMs = 0L

    private val _uiState = MutableStateFlow(
        SeaUiState(
            seaState = SeaState(
                t = AtmosphereMath.hoursNow(null),
                doy = AtmosphereMath.doyNow(),
            ),
        ),
    )
    val uiState: StateFlow<SeaUiState> = _uiState.asStateFlow()

    val renderer: SeaRenderer = SeaRenderer(
        onTelemetry = { stats, solarElDeg, simHours ->
            val nowMs = SystemClock.uptimeMillis()
            val shouldUpdateTime = _uiState.value.seaState.realTime ||
                _uiState.value.seaState.fastDay ||
                nowMs - lastUiTimeSyncMs > 300L
            if (shouldUpdateTime) lastUiTimeSyncMs = nowMs
            _uiState.update { current ->
                val updatedSea = if (shouldUpdateTime) {
                    current.seaState.copy(t = simHours)
                } else {
                    current.seaState
                }
                val hh = AtmosphereMath.formatHours(updatedSea.t)
                val defaultStatus =
                    "Солнце ${solarElDeg.toInt()}° · ветер ${(updatedSea.wind * 100).toInt()}% · облака ${(updatedSea.cloud * 100).toInt()}% · время $hh"
                current.copy(
                    seaState = updatedSea,
                    fpsStats = stats,
                    solarElevationDeg = solarElDeg,
                    statusMessage = if (current.statusMessage.isEmpty() || current.statusMessage.startsWith("Солнце ")) {
                        defaultStatus
                    } else {
                        current.statusMessage
                    },
                )
            }
        },
        onError = { err ->
            _uiState.update { it.copy(glError = err) }
        },
    )

    private val orientationController = OrientationController(appContext) { yawG, pitG, roll, tiltTx, tiltTy ->
        renderer.yawG = yawG
        renderer.pitG = pitG
        renderer.roll = roll
        renderer.tiltTx = tiltTx
        renderer.tiltTy = tiltTy
    }

    init {
        viewModelScope.launch {
            val saved = prefsRepo.preferencesFlow.first()
            timeRevision++
            qualityRevision++
            val gyroEnabled = saved.gyroMode && orientationController.isAvailable
            if (gyroEnabled) {
                orientationController.setGyroEnabled(true)
            }
            _uiState.update {
                it.copy(
                    seaState = it.seaState.copy(
                        lat = saved.lat,
                        lon = saved.lon,
                        qualityMode = saved.qualityMode,
                        gyroMode = gyroEnabled,
                        wind = saved.wind,
                        cloud = saved.cloud,
                        rain = saved.rain,
                        fog = saved.fog,
                        storm = saved.storm,
                        cam = saved.cam,
                        windDir = saved.windDir,
                        realTime = saved.realTime,
                        fastDay = saved.fastDay,
                        t = if (saved.realTime) AtmosphereMath.hoursNow(null) else saved.manualTimeHours,
                        doy = AtmosphereMath.doyNow(),
                        lhOn = saved.lhOn,
                        lhRotate = saved.lhRotate,
                        shipOn = saved.shipOn,
                        birdsOn = saved.birdsOn,
                        dropsOn = saved.dropsOn,
                        fastShip = saved.fastShip,
                    ),
                )
            }
            pushStateToRenderer()
            if (saved.autoMode) {
                enableAutoModeInternal(skipPermissionRequest = !locationHelper.hasLocationPermission())
            }
        }
    }

    private fun pushStateToRenderer() {
        renderer.updateConfig(_uiState.value.seaState, timeRevision, qualityRevision)
    }

    private fun persistCurrentState(immediate: Boolean = false) {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            if (!immediate) delay(200L)
            prefsRepo.saveSeaState(_uiState.value.seaState)
        }
    }

    fun onResume() {
        orientationController.start()
    }

    fun onPause() {
        orientationController.stop()
    }

    fun toggleControls() {
        _uiState.update { it.copy(isControlsOpen = !it.isControlsOpen) }
    }

    fun toggleFps() {
        _uiState.update { it.copy(showFps = !it.showFps) }
    }

    fun onDragLook(dx: Float, dy: Float) {
        renderer.yawD -= dx * 0.22
        renderer.pitD = AtmosphereMath.clamp(renderer.pitD + dy * 0.12, -30.0, 30.0)
    }

    fun onDoubleTapResetLook() {
        renderer.resetDragLook()
    }

    fun onToggleGyro() {
        if (!orientationController.isAvailable) {
            _uiState.update {
                it.copy(statusMessage = appContext.getString(R.string.status_gyro_unavailable))
            }
            return
        }
        val next = !_uiState.value.seaState.gyroMode
        orientationController.setGyroEnabled(next)
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(gyroMode = next),
                statusMessage = if (next) {
                    appContext.getString(R.string.status_gyro_active)
                } else {
                    ""
                },
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onCallShip() {
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(shipOn = true),
                statusMessage = appContext.getString(R.string.status_ship_called),
            )
        }
        pushStateToRenderer()
        renderer.requestSpawnShip(_uiState.value.seaState.fastShip)
    }

    fun onAutoButtonClicked(requestLocationPermission: () -> Unit) {
        if (_uiState.value.seaState.autoMode) {
            disableAutoMode()
        } else {
            if (locationHelper.hasLocationPermission()) {
                enableAutoModeInternal(skipPermissionRequest = false)
            } else {
                requestLocationPermission()
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        enableAutoModeInternal(skipPermissionRequest = !granted)
    }

    private fun disableAutoMode() {
        autoJob?.cancel()
        autoJob = null
        viewModelScope.launch { prefsRepo.setAutoMode(false) }
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(autoMode = false),
                sourceStatus = "",
            )
        }
        pushStateToRenderer()
    }

    private fun enableAutoModeInternal(skipPermissionRequest: Boolean) {
        autoJob?.cancel()
        timeRevision++
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    autoMode = true,
                    realTime = true,
                    fastDay = false,
                ),
                activeWeatherPreset = null,
                isRainbowActive = false,
                activeTimePreset = null,
                sourceStatus = appContext.getString(R.string.status_detecting_location),
            )
        }
        pushStateToRenderer()

        autoJob = viewModelScope.launch {
            prefsRepo.setAutoMode(true)
            val coords = if (!skipPermissionRequest) locationHelper.getCurrentCoordinates() else null
            if (coords != null) {
                _uiState.update {
                    it.copy(seaState = it.seaState.copy(lat = coords.first, lon = coords.second))
                }
                prefsRepo.setCoordinates(coords.first, coords.second)
            } else {
                _uiState.update {
                    it.copy(sourceStatus = appContext.getString(R.string.status_location_fallback))
                }
            }
            while (isActive) {
                fetchAndApplyWeather()
                delay(600_000L)
            }
        }
    }

    private suspend fun fetchAndApplyWeather() {
        val current = _uiState.value.seaState
        try {
            val snap = weatherRepo.fetchWeather(current.lat, current.lon)
            timeRevision++
            val updatedHours = AtmosphereMath.hoursNow(snap.utcOffsetSeconds)
            _uiState.update { state ->
                val nextSea = state.seaState.copy(
                    utcOffsetSeconds = snap.utcOffsetSeconds,
                    t = updatedHours,
                    wind = snap.wind,
                    cloud = snap.cloud,
                    rain = snap.rain,
                    fog = snap.fog,
                    storm = snap.storm,
                    windDir = snap.windDir,
                    realTime = true,
                    fastDay = false,
                )
                state.copy(
                    seaState = nextSea,
                    sourceStatus = "Авто · Open-Meteo · ${"%.2f".format(nextSea.lat)}°, ${"%.2f".format(nextSea.lon)}° · ${snap.windSpeedKmh.toInt()} км/ч, облачность ${snap.cloudCoverPercent.toInt()}% · обновлено ${AtmosphereMath.formatHours(updatedHours)}",
                )
            }
            pushStateToRenderer()
            persistCurrentState(immediate = true)
        } catch (_: Exception) {
            _uiState.update {
                it.copy(sourceStatus = appContext.getString(R.string.status_weather_error))
            }
        }
    }

    fun onCityQueryChanged(query: String) {
        _uiState.update { it.copy(cityQuery = query) }
    }

    fun onSearchCity() {
        val q = _uiState.value.cityQuery.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingCity = true) }
            try {
                val res = weatherRepo.findCity(q)
                if (res == null) {
                    _uiState.update {
                        it.copy(
                            isSearchingCity = false,
                            sourceStatus = appContext.getString(R.string.status_city_not_found),
                        )
                    }
                    return@launch
                }
                autoJob?.cancel()
                prefsRepo.setAutoMode(true)
                prefsRepo.setCoordinates(res.latitude, res.longitude)
                _uiState.update {
                    it.copy(
                        isSearchingCity = false,
                        seaState = it.seaState.copy(
                            lat = res.latitude,
                            lon = res.longitude,
                            autoMode = true,
                            realTime = true,
                            fastDay = false,
                        ),
                        activeWeatherPreset = null,
                        isRainbowActive = false,
                        activeTimePreset = null,
                    )
                }
                pushStateToRenderer()
                autoJob = viewModelScope.launch {
                    while (isActive) {
                        fetchAndApplyWeather()
                        delay(600_000L)
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSearchingCity = false,
                        sourceStatus = appContext.getString(R.string.status_network_error),
                    )
                }
            }
        }
    }

    private fun switchToManual() {
        if (_uiState.value.seaState.autoMode) {
            autoJob?.cancel()
            autoJob = null
            viewModelScope.launch { prefsRepo.setAutoMode(false) }
            _uiState.update {
                it.copy(
                    seaState = it.seaState.copy(autoMode = false),
                    sourceStatus = "",
                )
            }
        }
    }

    fun onSelectRainbow() {
        switchToManual()
        val s = _uiState.value.seaState
        val doy = AtmosphereMath.doyNow()
        val st = AtmosphereMath.sunTimes(s.lat, doy)
        val tRainbow = (st.sunsetHours - 2.2 + 24.0) % 24.0
        val sp = AtmosphereMath.solar(tRainbow, s.lat, doy)
        val cam = (((sp.az / AtmosphereMath.D + 180.0) + 180.0) % 360.0 + 360.0) % 360.0
        timeRevision++
        renderer.resetDragLook()
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    t = tRainbow,
                    doy = doy,
                    realTime = false,
                    fastDay = false,
                    wind = 0.3,
                    cloud = 0.5,
                    rain = 0.2,
                    fog = 0.0,
                    storm = 0.0,
                    cam = cam,
                    windDir = (cam + 160.0) % 360.0,
                ),
                isRainbowActive = true,
                activeWeatherPreset = null,
                activeTimePreset = null,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onSelectWeatherPreset(preset: WeatherPreset) {
        switchToManual()
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    wind = preset.wind,
                    cloud = preset.cloud,
                    rain = preset.rain,
                    fog = preset.fog,
                    storm = preset.storm,
                ),
                activeWeatherPreset = preset,
                isRainbowActive = false,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onSelectTimePreset(preset: TimePreset) {
        switchToManual()
        val s = _uiState.value.seaState
        val doy = AtmosphereMath.doyNow()
        val st = AtmosphereMath.sunTimes(s.lat, doy)
        val raw = when (preset) {
            TimePreset.DAWN -> st.sunriseHours + 0.3
            TimePreset.NOON -> 12.0
            TimePreset.SUNSET -> st.sunsetHours - 0.4
            TimePreset.DUSK -> st.sunsetHours + 0.8
            TimePreset.NIGHT -> 23.5
        }
        val tNext = ((raw % 24.0) + 24.0) % 24.0
        timeRevision++
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    t = tNext,
                    doy = doy,
                    realTime = false,
                    fastDay = false,
                ),
                activeTimePreset = preset,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onTimeChanged(hours: Double) {
        switchToManual()
        timeRevision++
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    t = hours.coerceIn(0.0, 24.0),
                    realTime = false,
                    fastDay = false,
                ),
                activeTimePreset = null,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = false)
    }

    fun onWindChanged(wind: Double) {
        switchToManual()
        _uiState.update {
            val s = it.seaState
            it.copy(
                seaState = s.copy(
                    wind = wind,
                    storm = if (s.rain > 0.8 && wind > 0.8) 1.0 else 0.0,
                ),
                activeWeatherPreset = null,
                isRainbowActive = false,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = false)
    }

    fun onCloudChanged(cloud: Double) {
        switchToManual()
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(cloud = cloud),
                activeWeatherPreset = null,
                isRainbowActive = false,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = false)
    }

    fun onRainChanged(rain: Double) {
        switchToManual()
        _uiState.update {
            val s = it.seaState
            it.copy(
                seaState = s.copy(
                    rain = rain,
                    storm = if (rain > 0.8 && s.wind > 0.8) 1.0 else 0.0,
                ),
                activeWeatherPreset = null,
                isRainbowActive = false,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = false)
    }

    fun onFogChanged(fog: Double) {
        switchToManual()
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(fog = fog),
                activeWeatherPreset = null,
                isRainbowActive = false,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = false)
    }

    fun onCamChanged(camDeg: Double) {
        _uiState.update {
            val s = it.seaState
            val nextWindDir = ((s.windDir - s.cam + camDeg) % 360.0 + 360.0) % 360.0
            it.copy(
                seaState = s.copy(cam = camDeg, windDir = nextWindDir),
                isRainbowActive = false,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = false)
    }

    fun onToggleLh(enabled: Boolean) {
        _uiState.update { it.copy(seaState = it.seaState.copy(lhOn = enabled)) }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onToggleLhRotate(enabled: Boolean) {
        _uiState.update { it.copy(seaState = it.seaState.copy(lhRotate = enabled)) }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onToggleShip(enabled: Boolean) {
        _uiState.update { it.copy(seaState = it.seaState.copy(shipOn = enabled)) }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onToggleBirds(enabled: Boolean) {
        _uiState.update { it.copy(seaState = it.seaState.copy(birdsOn = enabled)) }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onToggleDrops(enabled: Boolean) {
        _uiState.update { it.copy(seaState = it.seaState.copy(dropsOn = enabled)) }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onToggleFastShip(enabled: Boolean) {
        _uiState.update { it.copy(seaState = it.seaState.copy(fastShip = enabled)) }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onToggleRealTime(enabled: Boolean) {
        timeRevision++
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    realTime = enabled,
                    fastDay = if (enabled) false else it.seaState.fastDay,
                ),
                activeTimePreset = null,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onToggleFastDay(enabled: Boolean) {
        switchToManual()
        timeRevision++
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    fastDay = enabled,
                    realTime = if (enabled) false else it.seaState.realTime,
                ),
                activeTimePreset = null,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onQualityChanged(mode: QualityMode) {
        qualityRevision++
        viewModelScope.launch { prefsRepo.setQualityMode(mode) }
        _uiState.update {
            it.copy(seaState = it.seaState.copy(qualityMode = mode))
        }
        pushStateToRenderer()
    }

    override fun onCleared() {
        super.onCleared()
        orientationController.stop()
        autoJob?.cancel()
        persistJob?.cancel()
    }
}
