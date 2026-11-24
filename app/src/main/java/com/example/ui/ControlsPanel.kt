package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FilterDrama
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Sailing
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.simulation.AtmosphereMath
import com.example.simulation.LighthouseMode
import com.example.simulation.QualityMode
import com.example.simulation.TimePreset
import com.example.simulation.WallpaperScrollMode
import com.example.simulation.WeatherPreset
import com.example.ui.theme.ChipBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.OceanBlue
import com.example.ui.theme.SeaFoamInk
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SunAmber
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlsPanel(
    uiState: SeaUiState,
    onToggleAuto: () -> Unit,
    onToggleGyro: () -> Unit,
    onCallShip: () -> Unit,
    onSetLiveWallpaper: () -> Unit,
    onFrameAllDay: () -> Unit,
    onFrameAllNight: () -> Unit,
    onSelectRainbow: () -> Unit,
    onSelectWeatherPreset: (WeatherPreset) -> Unit,
    onSelectTimePreset: (TimePreset) -> Unit,
    onTimeChanged: (Double) -> Unit,
    onWindChanged: (Double) -> Unit,
    onCloudChanged: (Double) -> Unit,
    onRainChanged: (Double) -> Unit,
    onFogChanged: (Double) -> Unit,
    onCamChanged: (Double) -> Unit,
    onToggleLh: (Boolean) -> Unit,
    onSelectLhMode: (LighthouseMode) -> Unit,
    onToggleShip: (Boolean) -> Unit,
    onToggleBirds: (Boolean) -> Unit,
    onToggleDrops: (Boolean) -> Unit,
    onToggleFastShip: (Boolean) -> Unit,
    onToggleRealTime: (Boolean) -> Unit,
    onToggleFastDay: (Boolean) -> Unit,
    onToggleFps: () -> Unit,
    onSelectScrollMode: (WallpaperScrollMode) -> Unit,
    onScreenCountChanged: (Int) -> Unit,
    onSelectHomeScreen: (Int) -> Unit,
    onScrollSpanChanged: (Double) -> Unit,
    onToggleScrollReverse: (Boolean) -> Unit,
    onPreviewScreen: (Int?) -> Unit,
    onCityQueryChanged: (String) -> Unit,
    onSearchCity: () -> Unit,
    onQualityChanged: (QualityMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = uiState.seaState
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .heightIn(max = 540.dp)
            .testTag("controls_panel"),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top primary action chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionPillChip(
                    label = stringResource(R.string.action_auto),
                    icon = Icons.Rounded.MyLocation,
                    selected = s.autoMode,
                    onClick = onToggleAuto,
                    testTag = "auto_button",
                )
                ActionPillChip(
                    label = stringResource(R.string.action_gyro),
                    icon = Icons.Rounded.ScreenRotation,
                    selected = s.gyroMode,
                    onClick = onToggleGyro,
                    testTag = "gyro_button",
                )
                ActionPillChip(
                    label = stringResource(R.string.action_call_ship),
                    icon = Icons.Rounded.Sailing,
                    selected = false,
                    onClick = onCallShip,
                    testTag = "call_ship_button",
                )
                ActionPillChip(
                    label = stringResource(R.string.action_set_wallpaper),
                    icon = Icons.Rounded.Wallpaper,
                    selected = false,
                    onClick = onSetLiveWallpaper,
                    testTag = "set_wallpaper_button",
                )
            }

            if (uiState.sourceStatus.isNotEmpty()) {
                Text(
                    text = uiState.sourceStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("source_status_text"),
                )
            }

            // Weather presets + Rainbow + All-in-one framing
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PresetChip(
                    label = "Радуга",
                    selected = uiState.isRainbowActive,
                    onClick = onSelectRainbow,
                    testTag = "preset_rainbow",
                )
                PresetChip(
                    label = stringResource(R.string.action_frame_all_day),
                    selected = false,
                    onClick = onFrameAllDay,
                    testTag = "preset_frame_all_day",
                )
                PresetChip(
                    label = stringResource(R.string.action_frame_all_night),
                    selected = false,
                    onClick = onFrameAllNight,
                    testTag = "preset_frame_all_night",
                )
                WeatherPreset.entries.forEach { preset ->
                    PresetChip(
                        label = preset.label,
                        selected = uiState.activeWeatherPreset == preset,
                        onClick = { onSelectWeatherPreset(preset) },
                        testTag = "preset_weather_${preset.name.lowercase()}",
                    )
                }
            }

            // Time-of-day presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TimePreset.entries.forEach { preset ->
                    PresetChip(
                        label = preset.label,
                        selected = uiState.activeTimePreset == preset,
                        onClick = { onSelectTimePreset(preset) },
                        testTag = "preset_time_${preset.name.lowercase()}",
                    )
                }
            }

            HorizontalDivider(color = GlassBorder)

            // Sliders
            SeaSliderRow(
                label = stringResource(R.string.label_time),
                icon = Icons.Rounded.Schedule,
                value = s.t.toFloat(),
                valueRange = 0f..24f,
                trailingText = AtmosphereMath.formatHours(s.t),
                onValueChange = { onTimeChanged(it.toDouble()) },
                testTag = "slider_time",
            )
            SeaSliderRow(
                label = stringResource(R.string.label_wind),
                icon = Icons.Rounded.Air,
                value = s.wind.toFloat(),
                valueRange = 0f..1f,
                trailingText = "${(s.wind * 100).toInt()}%",
                onValueChange = { onWindChanged(it.toDouble()) },
                testTag = "slider_wind",
            )
            SeaSliderRow(
                label = stringResource(R.string.label_cloud),
                icon = Icons.Rounded.Cloud,
                value = s.cloud.toFloat(),
                valueRange = 0f..1f,
                trailingText = "${(s.cloud * 100).toInt()}%",
                onValueChange = { onCloudChanged(it.toDouble()) },
                testTag = "slider_cloud",
            )
            SeaSliderRow(
                label = stringResource(R.string.label_rain),
                icon = Icons.Rounded.WaterDrop,
                value = s.rain.toFloat(),
                valueRange = 0f..1f,
                trailingText = "${(s.rain * 100).toInt()}%",
                onValueChange = { onRainChanged(it.toDouble()) },
                testTag = "slider_rain",
            )
            SeaSliderRow(
                label = stringResource(R.string.label_fog),
                icon = Icons.Rounded.FilterDrama,
                value = s.fog.toFloat(),
                valueRange = 0f..1f,
                trailingText = "${(s.fog * 100).toInt()}%",
                onValueChange = { onFogChanged(it.toDouble()) },
                testTag = "slider_fog",
            )
            SeaSliderRow(
                label = stringResource(R.string.label_look),
                icon = Icons.Rounded.Explore,
                value = s.cam.toFloat(),
                valueRange = 0f..360f,
                trailingText = "${s.cam.toInt()}°",
                onValueChange = { onCamChanged(it.toDouble()) },
                testTag = "slider_cam",
            )

            HorizontalDivider(color = GlassBorder)

            // Explicit Gyroscope on/off switch in settings
            Surface(
                onClick = onToggleGyro,
                shape = RoundedCornerShape(14.dp),
                color = if (s.gyroMode) OceanBlue.copy(alpha = 0.22f) else ChipBackground,
                border = BorderStroke(
                    1.dp,
                    if (s.gyroMode) SkyCyan.copy(alpha = 0.6f) else GlassBorder,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gyro_settings_row"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = null,
                        tint = if (s.gyroMode) SkyCyan else SeaFoamInk.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.label_gyro_setting),
                            style = MaterialTheme.typography.labelLarge,
                            color = SeaFoamInk,
                        )
                        Text(
                            text = stringResource(R.string.desc_gyro_setting),
                            style = MaterialTheme.typography.bodySmall,
                            color = SeaFoamInk.copy(alpha = 0.7f),
                        )
                    }
                    Switch(
                        checked = s.gyroMode,
                        onCheckedChange = { onToggleGyro() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = OceanBlue,
                            uncheckedThumbColor = SeaFoamInk.copy(alpha = 0.8f),
                            uncheckedTrackColor = ChipBackground,
                            uncheckedBorderColor = GlassBorder,
                        ),
                        modifier = Modifier.testTag("gyro_settings_switch"),
                    )
                }
            }

            // Scene object & time mode toggles
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToggleChip(
                    label = stringResource(R.string.toggle_lighthouse),
                    icon = Icons.Rounded.Lightbulb,
                    checked = s.lhOn,
                    onCheckedChange = onToggleLh,
                    testTag = "toggle_lighthouse",
                )
                ToggleChip(
                    label = stringResource(R.string.toggle_ship),
                    icon = Icons.Rounded.Sailing,
                    checked = s.shipOn,
                    onCheckedChange = onToggleShip,
                    testTag = "toggle_ship",
                )
                ToggleChip(
                    label = stringResource(R.string.toggle_birds),
                    icon = Icons.Rounded.AutoAwesome,
                    checked = s.birdsOn,
                    onCheckedChange = onToggleBirds,
                    testTag = "toggle_birds",
                )
                ToggleChip(
                    label = stringResource(R.string.toggle_drops),
                    icon = Icons.Rounded.WaterDrop,
                    checked = s.dropsOn,
                    onCheckedChange = onToggleDrops,
                    testTag = "toggle_drops",
                )
                ToggleChip(
                    label = stringResource(R.string.toggle_fast_ship),
                    checked = s.fastShip,
                    onCheckedChange = onToggleFastShip,
                    testTag = "toggle_fast_ship",
                )
                ToggleChip(
                    label = stringResource(R.string.toggle_real_time),
                    checked = s.realTime,
                    onCheckedChange = onToggleRealTime,
                    testTag = "toggle_real_time",
                )
                ToggleChip(
                    label = stringResource(R.string.toggle_fast_day),
                    checked = s.fastDay,
                    onCheckedChange = onToggleFastDay,
                    testTag = "toggle_fast_day",
                )
                ToggleChip(
                    label = stringResource(R.string.toggle_fps),
                    icon = Icons.Rounded.Speed,
                    checked = uiState.showFps,
                    onCheckedChange = { onToggleFps() },
                    testTag = "toggle_fps",
                )
            }

            // 3 Lighthouse light modes when lighthouse is active
            if (s.lhOn) {
                Text(
                    text = stringResource(R.string.lighthouse_mode_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = SeaFoamInk.copy(alpha = 0.85f),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PresetChip(
                        label = stringResource(R.string.lh_mode_fixed),
                        selected = s.lhMode == LighthouseMode.FIXED,
                        onClick = { onSelectLhMode(LighthouseMode.FIXED) },
                        testTag = "lh_mode_fixed",
                    )
                    PresetChip(
                        label = stringResource(R.string.lh_mode_slow),
                        selected = s.lhMode == LighthouseMode.SLOW,
                        onClick = { onSelectLhMode(LighthouseMode.SLOW) },
                        testTag = "lh_mode_slow",
                    )
                    PresetChip(
                        label = stringResource(R.string.lh_mode_fast),
                        selected = s.lhMode == LighthouseMode.FAST,
                        onClick = { onSelectLhMode(LighthouseMode.FAST) },
                        testTag = "lh_mode_fast",
                    )
                }
            }

            HorizontalDivider(color = GlassBorder)

            // Wallpaper home-screen scrolling behavior
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ViewCarousel,
                    contentDescription = null,
                    tint = SkyCyan,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.wallpaper_scroll_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = SeaFoamInk.copy(alpha = 0.9f),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PresetChip(
                    label = stringResource(R.string.scroll_mode_fixed),
                    selected = s.scrollMode == WallpaperScrollMode.FIXED,
                    onClick = { onSelectScrollMode(WallpaperScrollMode.FIXED) },
                    testTag = "scroll_mode_fixed",
                )
                PresetChip(
                    label = stringResource(R.string.scroll_mode_panorama),
                    selected = s.scrollMode == WallpaperScrollMode.PANORAMA,
                    onClick = { onSelectScrollMode(WallpaperScrollMode.PANORAMA) },
                    testTag = "scroll_mode_panorama",
                )
            }

            if (s.scrollMode == WallpaperScrollMode.PANORAMA) {
                val effectivePages = if (uiState.detectedScreenCount >= 2) {
                    uiState.detectedScreenCount
                } else {
                    s.screenCount.coerceIn(2, 9)
                }
                val clampedHome = s.homeScreenIndex.coerceIn(1, effectivePages)
                val detectedText = when {
                    uiState.detectedScreenCount >= 2 ->
                        "Активных экранов рабочего стола: ${uiState.detectedScreenCount}"
                    uiState.detectedScreenCount == 1 ->
                        "Активных экранов: 1 (добавьте ещё стол, чтобы листать панораму)"
                    else ->
                        "Оболочка скрывает число экранов — укажите вручную (сейчас $effectivePages)"
                }
                Text(
                    text = detectedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyCyan,
                    modifier = Modifier.testTag("detected_screens_text"),
                )

                if (uiState.detectedScreenCount < 2) {
                    Text(
                        text = stringResource(R.string.label_screen_count),
                        style = MaterialTheme.typography.labelSmall,
                        color = SeaFoamInk.copy(alpha = 0.8f),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (count in 2..6) {
                            PresetChip(
                                label = "$count",
                                selected = s.screenCount.coerceIn(2, 9) == count,
                                onClick = { onScreenCountChanged(count) },
                                testTag = "screen_count_$count",
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.label_home_screen),
                    style = MaterialTheme.typography.labelSmall,
                    color = SeaFoamInk.copy(alpha = 0.8f),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (idx in 1..effectivePages) {
                        PresetChip(
                            label = if (idx == clampedHome) "$idx (главный)" else "$idx",
                            selected = idx == clampedHome,
                            onClick = { onSelectHomeScreen(idx) },
                            testTag = "home_screen_$idx",
                        )
                    }
                }

                SeaSliderRow(
                    label = stringResource(R.string.label_span),
                    icon = Icons.Rounded.SwapHoriz,
                    value = s.scrollSpanDeg.toFloat(),
                    valueRange = 20f..180f,
                    trailingText = "${s.scrollSpanDeg.roundToInt()}°",
                    onValueChange = { onScrollSpanChanged(it.toDouble()) },
                    testTag = "slider_scroll_span",
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToggleChip(
                        label = stringResource(
                            if (s.scrollReverse) R.string.scroll_dir_left else R.string.scroll_dir_right,
                        ),
                        checked = s.scrollReverse,
                        onCheckedChange = onToggleScrollReverse,
                        testTag = "toggle_scroll_reverse",
                    )
                }

                Text(
                    text = stringResource(R.string.label_preview_screen),
                    style = MaterialTheme.typography.labelSmall,
                    color = SeaFoamInk.copy(alpha = 0.8f),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val activePreview = uiState.previewScreenIndex ?: clampedHome
                    for (idx in 1..effectivePages) {
                        val homeFrac = (clampedHome - 1).toDouble() / (effectivePages - 1).toDouble()
                        val idxFrac = (idx - 1).toDouble() / (effectivePages - 1).toDouble()
                        val signedSpan = if (s.scrollReverse) -s.scrollSpanDeg else s.scrollSpanDeg
                        val angleDeg = ((s.cam + (idxFrac - homeFrac) * signedSpan) % 360.0 + 360.0) % 360.0
                        PresetChip(
                            label = "$idx (${angleDeg.roundToInt()}°)",
                            selected = activePreview == idx,
                            onClick = {
                                onPreviewScreen(if (idx == clampedHome) null else idx)
                            },
                            testTag = "preview_screen_$idx",
                        )
                    }
                }
            }

            HorizontalDivider(color = GlassBorder)

            // City search row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.cityQuery,
                    onValueChange = onCityQueryChanged,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.city_placeholder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            onSearchCity()
                        },
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = SeaFoamInk,
                        unfocusedTextColor = SeaFoamInk,
                        focusedContainerColor = ChipBackground,
                        unfocusedContainerColor = ChipBackground,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("city_input"),
                )
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onSearchCity()
                    },
                    enabled = !uiState.isSearchingCity,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OceanBlue,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("city_search_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_find),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // Quality selector
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PresetChip(
                    label = stringResource(R.string.quality_auto),
                    selected = s.qualityMode == QualityMode.AUTO,
                    onClick = { onQualityChanged(QualityMode.AUTO) },
                    testTag = "quality_auto",
                )
                PresetChip(
                    label = stringResource(R.string.quality_eco),
                    selected = s.qualityMode == QualityMode.ECO,
                    onClick = { onQualityChanged(QualityMode.ECO) },
                    testTag = "quality_eco",
                )
                PresetChip(
                    label = stringResource(R.string.quality_max),
                    selected = s.qualityMode == QualityMode.MAX,
                    onClick = { onQualityChanged(QualityMode.MAX) },
                    testTag = "quality_max",
                )
            }

            if (uiState.statusMessage.isNotEmpty()) {
                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("status_footer_text"),
                )
            }
        }
    }
}

@Composable
private fun ActionPillChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ChipBackground,
            labelColor = SeaFoamInk,
            iconColor = SkyCyan,
            selectedContainerColor = OceanBlue,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = GlassBorder,
            selectedBorderColor = OceanBlue,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag),
    )
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ChipBackground,
            labelColor = SeaFoamInk,
            selectedContainerColor = OceanBlue,
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = GlassBorder,
            selectedBorderColor = OceanBlue,
        ),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .testTag(testTag),
    )
}

@Composable
private fun ToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    icon: ImageVector? = null,
) {
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ChipBackground,
            labelColor = SeaFoamInk.copy(alpha = 0.78f),
            iconColor = SeaFoamInk.copy(alpha = 0.78f),
            selectedContainerColor = OceanBlue.copy(alpha = 0.35f),
            selectedLabelColor = SeaFoamInk,
            selectedLeadingIconColor = SkyCyan,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = checked,
            borderColor = GlassBorder,
            selectedBorderColor = SkyCyan.copy(alpha = 0.7f),
        ),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .testTag(testTag),
    )
}

@Composable
private fun SeaSliderRow(
    label: String,
    icon: ImageVector,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    trailingText: String,
    onValueChange: (Float) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SkyCyan,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SeaFoamInk.copy(alpha = 0.85f),
            modifier = Modifier.width(68.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = SunAmber,
                activeTrackColor = OceanBlue,
                inactiveTrackColor = ChipBackground,
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(testTag),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = trailingText,
            style = MaterialTheme.typography.labelSmall,
            color = SeaFoamInk,
            modifier = Modifier.width(44.dp),
        )
    }
}
