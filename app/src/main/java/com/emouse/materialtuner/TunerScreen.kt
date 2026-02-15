package com.emouse.materialtuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.systemBarsPadding


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen() {

    var note by remember { mutableStateOf("G") }
    var hz by remember { mutableStateOf(196.0) }
    var cents by remember { mutableStateOf(-12f) }
    var isListening by remember { mutableStateOf(true) }

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

            // NOTE
            Text(
                text = note,
                fontSize = 96.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = String.format("%.1f Hz", hz),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )

            Spacer(Modifier.height(32.dp))

            StatusBadge(
                cents = cents,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            TunerMeter(
                cents = cents,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.weight(1f))

            // Start / Stop button
            FilledTonalButton(
                onClick = { isListening = !isListening },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
                    .height(70.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    if (isListening) "Stop" else "Start",
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
    val clamped = cents.coerceIn(-20f, 20f)
    val position = (clamped + 20f) / 40f

    Surface(
        modifier = modifier.height(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(30.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(gradient)
            )

            // Needle
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                val offset = ((position * 280f) - 140f).dp

                Surface(
                    modifier = Modifier
                        .offset(x = offset)
                        .width(14.dp)
                        .height(65.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(10.dp)
                ) {}
            }
        }
    }
}
