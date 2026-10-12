package com.maquis.caisse.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = GestionBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8FF),
    onPrimaryContainer = Color(0xFF0A2A66),
    secondary = GestionCyan,
    onSecondary = Color.White,
    tertiary = GestionSuccess,
    error = GestionDanger,
    onError = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
)

/** Thème lumineux forcé (comptoir) — fond dégradé vivant. */
@Composable
fun MaquisCaisseTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = LightColors, typography = MaquisTypography) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            LightBackground,
                            LightBackgroundEnd,
                            Color(0xFFE3F2FD),
                        ),
                    ),
                ),
        ) {
            // Réduit toute l'interface proportionnellement sur les petits écrans.
            AdaptiveScaleHost(content)
        }
    }
}
