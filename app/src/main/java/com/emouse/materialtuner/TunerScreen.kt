package com.emouse.materialtuner

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen() {

    val engine = remember { TunerEngine() }
    DisposableEffect(Unit) {
        onDispose { engine.stop() }
    }

    var uiState by remember { mutableStateOf(TunerState()) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        engine.setMicPermission(granted)
    }

    LaunchedEffect(Unit) {
        engine.state.collectLatest { uiState = it }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Carminic Tuner") },
                actions = {
                    IconButton(onClick = { /* TODO: Settings */ }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(40.dp))

            Text(
                text = uiState.note,
                fontSize = 96.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (uiState.hz > 0) String.format("%.1f Hz", uiState.hz) else "-- Hz",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )

            Spacer(Modifier.height(32.dp))

            StatusBadge(
                cents = uiState.cents,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            TunerMeter(
                cents = uiState.cents,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.weight(1f))

            FilledTonalButton(
                onClick = {
                    if (!uiState.hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@FilledTonalButton
                    }
                    if (uiState.isListening) engine.stop() else engine.start()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
                    .height(70.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = if (uiState.isListening) "Stop" else "Start",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    cents: Float,
    modifier: Modifier = Modifier
) {
    val label = when {
        cents <= -2f -> "FLAT"
        cents >= 2f -> "SHARP"
        else -> "IN TUNE"
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "${cents.roundToInt()} cents",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TunerMeter(
    cents: Float,
    modifier: Modifier = Modifier
) {
    // Animate cents so the needle glides instead of jumping
    val target = cents.coerceIn(-20f, 20f)

    val animatedCents by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            stiffness = 500f,
            dampingRatio = 0.85f
        ),
        label = "needle"
    )

    // Map -20..+20 -> 0..1
    val position = (animatedCents + 20f) / 40f

    val containerHorizontalPadding = 24.dp
    val trackHeight = 40.dp
    val needleWidth = 14.dp
    val needleHeight = 65.dp

    Surface(
        modifier = modifier.height(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(30.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = containerHorizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            val gradient = Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.error
                )
            )

            // Track (always centered)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(100.dp))
                    .background(gradient)
            )

            // Needle (positioned relative to available width, clamped)
            val trackWidth = maxWidth
            val needleOffset = (trackWidth - needleWidth) * position

            Surface(
                modifier = Modifier
                    .offset(x = needleOffset.coerceIn(0.dp, trackWidth - needleWidth))
                    .align(Alignment.CenterStart)
                    .width(needleWidth)
                    .height(needleHeight),
                color = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(10.dp)
            ) {}
        }
    }
}

