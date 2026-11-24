package com.example.ui

import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.OceanAbyss
import com.example.ui.theme.OceanBlue
import com.example.ui.theme.SeaFoamInk
import com.example.wallpaper.SeaWallpaperService

@Composable
fun SeaScreen(
    viewModel: SeaViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    BackHandler(enabled = uiState.isControlsOpen) {
        viewModel.toggleControls()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onLocationPermissionResult(granted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OceanAbyss),
    ) {
        SeaGlSurfaceView(
            renderer = viewModel.renderer,
            onDragLook = viewModel::onDragLook,
            onDoubleTapReset = viewModel::onDoubleTapResetLook,
            modifier = Modifier
                .fillMaxSize()
                .testTag("sea_gl_surface"),
        )

        val glErr = uiState.glError
        if (glErr != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF37506E), Color(0xFF0E2236)),
                        ),
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${stringResource(R.string.error_gl_unavailable)}\n$glErr",
                    style = MaterialTheme.typography.titleMedium,
                    color = SeaFoamInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("gl_error_message"),
                )
            }
        }

        if (uiState.showFps) {
            val stats = uiState.fpsStats
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 12.dp)
                    .testTag("fps_hud"),
                shape = RoundedCornerShape(999.dp),
                color = GlassSurface,
                border = BorderStroke(1.dp, GlassBorder),
            ) {
                Text(
                    text = "${stats.fps} к/с · сцена ${stats.sceneWidth}×${stats.sceneHeight} · экран ${stats.screenWidth}×${stats.screenHeight}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeaFoamInk.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // Top-right floating HUD actions: live wallpaper, quick gyroscope toggle, settings button
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { launchLiveWallpaperPicker(context) })
                    }
                    .testTag("quick_wallpaper_button"),
                shape = CircleShape,
                color = GlassSurface,
                border = BorderStroke(1.dp, GlassBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Wallpaper,
                        contentDescription = stringResource(R.string.action_set_wallpaper),
                        tint = SeaFoamInk,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            val gyroActive = uiState.seaState.gyroMode
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { viewModel.onToggleGyro() })
                    }
                    .testTag("quick_gyro_button"),
                shape = CircleShape,
                color = if (gyroActive) OceanBlue else GlassSurface,
                border = BorderStroke(1.dp, if (gyroActive) OceanBlue else GlassBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = stringResource(R.string.action_gyro),
                        tint = if (gyroActive) Color.White else SeaFoamInk,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { viewModel.toggleControls() },
                            onDoubleTap = { viewModel.toggleFps() },
                        )
                    }
                    .testTag("settings_button"),
                shape = CircleShape,
                color = GlassSurface,
                border = BorderStroke(1.dp, GlassBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (uiState.isControlsOpen) Icons.Rounded.Close else Icons.Rounded.Tune,
                        contentDescription = stringResource(
                            if (uiState.isControlsOpen) R.string.cd_close_settings else R.string.cd_settings,
                        ),
                        tint = SeaFoamInk,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isControlsOpen,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                slideOutVertically(targetOffsetY = { it / 4 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            ControlsPanel(
                uiState = uiState,
                onToggleAuto = {
                    viewModel.onAutoButtonClicked {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
                onToggleGyro = viewModel::onToggleGyro,
                onCallShip = viewModel::onCallShip,
                onSetLiveWallpaper = { launchLiveWallpaperPicker(context) },
                onSelectRainbow = viewModel::onSelectRainbow,
                onSelectWeatherPreset = viewModel::onSelectWeatherPreset,
                onSelectTimePreset = viewModel::onSelectTimePreset,
                onTimeChanged = viewModel::onTimeChanged,
                onWindChanged = viewModel::onWindChanged,
                onCloudChanged = viewModel::onCloudChanged,
                onRainChanged = viewModel::onRainChanged,
                onFogChanged = viewModel::onFogChanged,
                onCamChanged = viewModel::onCamChanged,
                onToggleLh = viewModel::onToggleLh,
                onToggleLhRotate = viewModel::onToggleLhRotate,
                onToggleShip = viewModel::onToggleShip,
                onToggleBirds = viewModel::onToggleBirds,
                onToggleDrops = viewModel::onToggleDrops,
                onToggleFastShip = viewModel::onToggleFastShip,
                onToggleRealTime = viewModel::onToggleRealTime,
                onToggleFastDay = viewModel::onToggleFastDay,
                onToggleFps = viewModel::toggleFps,
                onCityQueryChanged = viewModel::onCityQueryChanged,
                onSearchCity = viewModel::onSearchCity,
                onQualityChanged = viewModel::onQualityChanged,
            )
        }
    }
}

private fun launchLiveWallpaperPicker(context: Context) {
    val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, SeaWallpaperService::class.java),
        )
    }
    try {
        context.startActivity(directIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.status_wallpaper_picker_error),
                Toast.LENGTH_LONG,
            ).show()
        }
    } catch (_: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.status_wallpaper_picker_error),
            Toast.LENGTH_LONG,
        ).show()
    }
}
