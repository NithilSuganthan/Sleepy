package com.sleepmusictimer.app.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepmusictimer.app.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    preferencesRepository: PreferencesRepository,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToMain: () -> Unit
) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Run animation in parallel
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600)
            )
        }
        
        // Wait 700ms total before transition
        kotlinx.coroutines.delay(700)
        
        val settings = preferencesRepository.sleepSettingsFlow.first()
        if (settings.hasCompletedOnboarding) {
            onNavigateToMain()
        } else {
            onNavigateToOnboarding()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha.value).scale(scale.value)
        ) {
            // Perfect crescent moon drawn on canvas
            Canvas(modifier = Modifier.size(120.dp)) {
                val r = size.width / 2
                drawCircle(
                    color = Color(0xFFC5CAE9), // Moonlight blue-grey
                    radius = r,
                    center = Offset(r, r)
                )
                // Overlap circle to make a crescent mask
                drawCircle(
                    color = Color.Black,
                    radius = r * 0.95f,
                    center = Offset(r * 1.35f, r * 0.65f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "SLEEPY",
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "We'll handle the music",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
        }
    }
}
