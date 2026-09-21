package com.maxrave.simpmusic.ui.screen.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.earix_splash

private const val SPLASH_MIN_DISPLAY_MS = 1500L
private const val SPLASH_FADE_MS = 500

/**
 * Earix cold-start splash (APPROACH A glow + APPROACH B overlay).
 *
 * Full-screen splash art with a soft pulsing purple radial glow (the "earphone
 * glow" — generic placement so no precise wire coordinates are needed).
 * Shown for at least [SPLASH_MIN_DISPLAY_MS], then fades out over 500ms.
 *
 * Cold start only: [showSplash] is exposable via [rememberSaveable], so rotation
 * and background/foreground cycles keep it hidden — only a fresh process shows it.
 * If the image fails to decode, the deep-purple fallback background shows instead.
 */
@Composable
fun EarixSplashOverlay() {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_DISPLAY_MS)
        showSplash = false
    }
    AnimatedVisibility(
        visible = showSplash,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(SPLASH_FADE_MS)),
    ) {
        val glowAlpha by rememberInfiniteTransition(label = "splashGlow").animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1600),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "splashGlowAlpha",
        )
        // Fallback color first: if the art fails to load this is all the user sees.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0618)),
        ) {
            Image(
                painter = painterResource(Res.drawable.earix_splash),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to Color(0xFFA855F7).copy(alpha = glowAlpha * 0.35f),
                            0.7f to Color.Transparent,
                        ),
                    ),
            )
        }
    }
}
