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
import com.example.simulation.LighthouseMode
import com.example.simulation.QualityMode
import com.example.simulation.SeaRenderer
import com.example.simulation.SeaState
import com.example.simulation.TimePreset
import com.example.simulation.WallpaperScrollMode
import com.example.simulation.WeatherPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val detectedScreenCount: Int = 0,
    val previewScreenIndex: Int? = null,
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
        if (_uiState.value.seaState.gyroMode) {
            renderer.yawG = yawG
            renderer.pitG = pitG
            renderer.roll = roll
            renderer.tiltTx = tiltTx
            renderer.tiltTy = tiltTy
        }
    }

    init {
        viewModelScope.launch {
            val saved = prefsRepo.preferencesFlow.first()
            timeRevision++
            qualityRevision++
            val gyroEnabled = saved.gyroMode && orientationController.isAvailable
            if (gyroEnabled) {
                orientationController.setGyroEnabled(true)
                orientationController.start()
            } else {
                orientationController.setGyroEnabled(false)
            }
            _uiState.update {
                it.copy(
                    detectedScreenCount = saved.detectedScreenCount,
                    seaState = it.seaState.copy(
                        lat = saved.lat,
                        lon = saved.lon,
                        qualityMode = saved.qualityMode,
                        gyroMode = gyroEnabled,
                        wind = saved.wind,
                        cloud = saved.cloud,
                        cloudLow = saved.cloudLow,
                        cloudMid = saved.cloudMid,
                        cloudHigh = saved.cloudHigh,
                        rain = saved.rain,
                        fog = saved.fog,
                        storm = saved.storm,
                        cam = saved.cam,
                        camPitch = saved.camPitch,
                        windDir = saved.windDir,
                        realTime = saved.realTime,
                        fastDay = saved.fastDay,
                        t = if (saved.realTime) AtmosphereMath.hoursNow(null) else saved.manualTimeHours,
                        doy = AtmosphereMath.doyNow(),
                        lhOn = saved.lhOn,
                        lhMode = saved.lhMode,
                        shipOn = saved.shipOn,
                        birdsOn = saved.birdsOn,
                        dropsOn = saved.dropsOn,
                        fastShip = saved.fastShip,
                        scrollMode = saved.scrollMode,
                        screenCount = saved.screenCount,
                        homeScreenIndex = saved.homeScreenIndex,
                        scrollSpanDeg = saved.scrollSpanDeg,
                        scrollReverse = saved.scrollReverse,
                    ),
                )
            }
            renderer.yawD = 0.0
            renderer.pitD = saved.camPitch
            pushStateToRenderer()
            if (saved.autoMode) {
                enableAutoModeInternal(skipPermissionRequest = !locationHelper.hasLocationPermission())
            }
        }

        viewModelScope.launch {
            prefsRepo.preferencesFlow
                .map { it.detectedScreenCount }
                .distinctUntilChanged()
                .collect { count ->
                    _uiState.update { it.copy(detectedScreenCount = count) }
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
        if (_uiState.value.seaState.gyroMode) {
            orientationController.start()
        }
    }

    fun onPause() {
        orientationController.stop()
    }

    fun toggleControls() {
        val nextOpen = !_uiState.value.isControlsOpen
        if (!nextOpen) {
            renderer.yawD = 0.0
        }
        _uiState.update {
            it.copy(
                isControlsOpen = nextOpen,
                previewScreenIndex = if (!nextOpen) null else it.previewScreenIndex,
            )
        }
    }

    fun toggleFps() {
        _uiState.update { it.copy(showFps = !it.showFps) }
    }

    fun onDragLook(dx: Float, dy: Float) {
        val s = _uiState.value.seaState
        val nextCam = ((s.cam - dx * 0.22) % 360.0 + 360.0) % 360.0
        val nextPitch = AtmosphereMath.clamp(s.camPitch + dy * 0.12, -30.0, 30.0)
        val nextWindDir = ((s.windDir - s.cam + nextCam) % 360.0 + 360.0) % 360.0
        renderer.yawD = 0.0
        renderer.pitD = nextPitch
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    cam = nextCam,
                    camPitch = nextPitch,
                    windDir = nextWindDir,
                ),
                previewScreenIndex = null,
                isRainbowActive = false,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = false)
    }

    fun onDoubleTapResetLook() {
        renderer.yawD = 0.0
        renderer.pitD = 0.0
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(camPitch = 0.0),
                previewScreenIndex = null,
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
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
        if (next) {
            orientationController.start()
        } else {
            orientationController.stop()
            renderer.yawG = 0.0
            renderer.pitG = 0.0
            renderer.roll = 0.0
            renderer.tiltTx = 0.0
            renderer.tiltTy = 0.0
        }
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
        persistCurrentState(immediate = true)
        renderer.requestSpawnShip(_uiState.value.seaState.fastShip)
    }

    fun onFrameAllTogether(night: Boolean) {
        switchToManual()
        val s = _uiState.value.seaState
        val doy = AtmosphereMath.doyNow()
        val targetCam: Double
        val targetHour: Double
        if (night) {
            val (h, c) = AtmosphereMath.findNightMoonLighthouseAlignment(s.lat, doy)
            targetHour = h
            targetCam = c
        } else {
            targetCam = 263.0
            targetHour = AtmosphereMath.findSunsetLighthouseHour(s.lat, doy, targetCam)
        }
        timeRevision++
        renderer.yawD = 0.0
        renderer.pitD = 0.0
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    t = targetHour,
                    doy = doy,
                    realTime = false,
                    fastDay = false,
                    cam = targetCam,
                    camPitch = 0.0,
                    lhOn = true,
                    shipOn = true,
                ),
                previewScreenIndex = null,
                activeTimePreset = null,
                isRainbowActive = false,
                statusMessage = appContext.getString(
                    if (night) R.string.status_framed_night else R.string.status_framed_day,
                ),
            )
        }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
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
                    cloudLow = snap.cloudLow,
                    cloudMid = snap.cloudMid,
                    cloudHigh = snap.cloudHigh,
                    rain = snap.rain,
                    fog = snap.fog,
                    storm = snap.storm,
                    windDir = snap.windDir,
                    realTime = true,
                    fastDay = false,
                )
                state.copy(
                    seaState = nextSea,
                    sourceStatus = "Авто · Open-Meteo · ${"%.2f".format(nextSea.lat)}°, ${"%.2f".format(nextSea.lon)}° · ${snap.windSpeedKmh.toInt()} км/ч, облачность ${snap.cloudCoverPercent.toInt()}% (слои ${(snap.cloudLow * 100).toInt()}/${(snap.cloudMid * 100).toInt()}/${(snap.cloudHigh * 100).toInt()}%) · обновлено ${AtmosphereMath.formatHours(updatedHours)}",
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
        renderer.yawD = 0.0
        renderer.pitD = 0.0
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    t = tRainbow,
                    doy = doy,
                    realTime = false,
                    fastDay = false,
                    wind = 0.3,
                    cloud = 0.5,
                    cloudLow = 0.42,
                    cloudMid = 0.48,
                    cloudHigh = 0.35,
                    rain = 0.2,
                    fog = 0.0,
                    storm = 0.0,
                    cam = cam,
                    camPitch = 0.0,
                    windDir = (cam + 160.0) % 360.0,
                ),
                previewScreenIndex = null,
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
        val (cLow, cMid, cHigh) = deriveCloudLayers(preset.cloud)
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    wind = preset.wind,
                    cloud = preset.cloud,
                    cloudLow = cLow,
                    cloudMid = cMid,
                    cloudHigh = cHigh,
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

    private fun deriveCloudLayers(cloud: Double): Triple<Double, Double, Double> {
        val low = AtmosphereMath.clamp(cloud * 0.85, 0.0, 1.0)
        val mid = AtmosphereMath.clamp(cloud * 0.95, 0.0, 1.0)
        val high = AtmosphereMath.clamp(cloud * 0.75 + 0.15 * (1.0 - cloud), 0.0, 1.0)
        return Triple(low, mid, high)
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
        val (cLow, cMid, cHigh) = deriveCloudLayers(cloud)
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    cloud = cloud,
                    cloudLow = cLow,
                    cloudMid = cMid,
                    cloudHigh = cHigh,
                ),
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
        updatePreviewOffset(_uiState.value.previewScreenIndex)
        pushStateToRenderer()
        persistCurrentState(immediate = false)
    }

    fun onToggleLh(enabled: Boolean) {
        _uiState.update { it.copy(seaState = it.seaState.copy(lhOn = enabled)) }
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onSelectLhMode(mode: LighthouseMode) {
        _uiState.update { it.copy(seaState = it.seaState.copy(lhMode = mode)) }
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

    fun onSelectScrollMode(mode: WallpaperScrollMode) {
        val nextPreview = if (mode == WallpaperScrollMode.FIXED) null else _uiState.value.previewScreenIndex
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(scrollMode = mode),
                previewScreenIndex = nextPreview,
            )
        }
        updatePreviewOffset(nextPreview)
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onSelectHomeScreen(index: Int) {
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(homeScreenIndex = index.coerceAtLeast(1)),
                previewScreenIndex = null,
            )
        }
        renderer.yawD = 0.0
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onScreenCountChanged(count: Int) {
        val clamped = count.coerceIn(2, 9)
        _uiState.update {
            it.copy(
                seaState = it.seaState.copy(
                    screenCount = clamped,
                    homeScreenIndex = it.seaState.homeScreenIndex.coerceIn(1, clamped),
                ),
                previewScreenIndex = null,
            )
        }
        renderer.yawD = 0.0
        pushStateToRenderer()
        persistCurrentState(immediate = true)
    }

    fun onScrollSpanChanged(deg: Double) {
        val clamped = deg.coerceIn(20.0, 180.0)
        _uiState.update {
            it.copy(seaState = it.seaState.copy(scrollSpanDeg = clamped))
        }
        updatePreviewOffset(_uiState.value.previewScreenIndex)
        persistCurrentState(immediate = false)
    }

    fun onToggleScrollReverse(reverse: Boolean) {
        _uiState.update {
            it.copy(seaState = it.seaState.copy(scrollReverse = reverse))
        }
        updatePreviewOffset(_uiState.value.previewScreenIndex)
        persistCurrentState(immediate = true)
    }

    fun onPreviewScreen(index: Int?) {
        _uiState.update { it.copy(previewScreenIndex = index) }
        updatePreviewOffset(index)
    }

    private fun updatePreviewOffset(previewIndex: Int?) {
        val state = _uiState.value
        val s = state.seaState
        if (previewIndex == null || s.scrollMode != WallpaperScrollMode.PANORAMA) {
            renderer.yawD = 0.0
            return
        }
        val effectivePages = if (state.detectedScreenCount >= 2) {
            state.detectedScreenCount
        } else {
            s.screenCount.coerceIn(2, 9)
        }
        val clampedHome = s.homeScreenIndex.coerceIn(1, effectivePages)
        val clampedPreview = previewIndex.coerceIn(1, effectivePages)
        val homeFraction = (clampedHome - 1).toDouble() / (effectivePages - 1).toDouble()
        val targetFraction = (clampedPreview - 1).toDouble() / (effectivePages - 1).toDouble()
        val signedSpan = if (s.scrollReverse) -s.scrollSpanDeg else s.scrollSpanDeg
        renderer.yawD = (targetFraction - homeFraction) * signedSpan
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
