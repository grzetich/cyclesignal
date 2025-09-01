package com.example.cyclesignal.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.cyclesignal.data.DisplayState

@Composable
fun CycleSignalScreen(
    displayState: DisplayState,
    currentTime: String,
    cardinalDirection: String,
    speed: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (displayState) {
        DisplayState.NEUTRAL_INFO -> NeutralInfoScreen(
            currentTime = currentTime,
            cardinalDirection = cardinalDirection,
            speed = speed,
            onSettingsClick = onSettingsClick,
            modifier = modifier
        )
        DisplayState.ARROW_LEFT -> ArrowScreen(
            direction = ArrowDirection.LEFT,
            modifier = modifier
        )
        DisplayState.ARROW_RIGHT -> ArrowScreen(
            direction = ArrowDirection.RIGHT,
            modifier = modifier
        )
        DisplayState.RED_FACE -> RedFaceScreen(
            modifier = modifier
        )
    }
}

@Composable
fun NeutralInfoScreen(
    currentTime: String,
    cardinalDirection: String,
    speed: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.display1,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = cardinalDirection,
                style = MaterialTheme.typography.title1,
                color = MaterialTheme.colors.onBackground
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = speed,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.onBackground
            )
        }
        
        // Settings button in bottom right corner
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = MaterialTheme.colors.onBackground,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(24.dp)
                .clickable { onSettingsClick() }
        )
    }
}

enum class ArrowDirection {
    LEFT, RIGHT
}

@Composable
fun ArrowScreen(
    direction: ArrowDirection,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (direction) {
                ArrowDirection.LEFT -> Icons.Default.ArrowBack
                ArrowDirection.RIGHT -> Icons.Default.ArrowForward
            },
            contentDescription = when (direction) {
                ArrowDirection.LEFT -> "Left Arrow"
                ArrowDirection.RIGHT -> "Right Arrow"
            },
            tint = Color.White,
            modifier = Modifier.size(80.dp)
        )
    }
}

@Composable
fun RedFaceScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Red)
    )
}