package com.example.ui

import android.Manifest
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

@Composable
fun SeaScreen(
    viewModel: SeaViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

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

        // Top-right floating HUD actions: quick gyroscope toggle + settings button
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
