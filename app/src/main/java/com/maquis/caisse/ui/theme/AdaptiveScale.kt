package com.maquis.caisse.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Taille de référence (en dp) pour laquelle l'interface a été dessinée.
 * Tant que l'écran est au moins aussi grand, rien ne change (échelle = 1).
 * Sur un écran plus petit, toute l'interface est réduite proportionnellement
 * pour tenir ; ce qui déborde malgré tout reste défilable.
 */
object AdaptiveScale {
    const val REF_WIDTH_DP = 1000f
    const val REF_HEIGHT_DP = 620f

    /** En dessous, on ne réduit plus (texte illisible) : le contenu défile. */
    const val MIN_SCALE = 0.55f

    fun scaleFor(widthDp: Float, heightDp: Float): Float {
        if (widthDp <= 0f || heightDp <= 0f) return 1f
        val s = minOf(1f, widthDp / REF_WIDTH_DP, heightDp / REF_HEIGHT_DP)
        return s.coerceIn(MIN_SCALE, 1f)
    }
}

/**
 * Enveloppe qui applique l'échelle à tout le contenu (dp et sp), dialogues compris.
 * Configuration.screenWidthDp/HeightDp sont aussi recalculés pour que les écrans
 * qui s'y réfèrent (ex. largeur de la barre latérale) restent cohérents.
 */
@Composable
fun AdaptiveScaleHost(content: @Composable () -> Unit) {
    val baseDensity = LocalDensity.current
    val baseConfig = LocalConfiguration.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = AdaptiveScale.scaleFor(maxWidth.value, maxHeight.value)
        if (scale >= 0.999f) {
            content()
        } else {
            val density = remember(baseDensity, scale) {
                Density(
                    density = baseDensity.density * scale,
                    fontScale = baseDensity.fontScale,
                )
            }
            val config = remember(baseConfig, scale) {
                Configuration(baseConfig).apply {
                    screenWidthDp = (baseConfig.screenWidthDp / scale).toInt()
                    screenHeightDp = (baseConfig.screenHeightDp / scale).toInt()
                    smallestScreenWidthDp = (baseConfig.smallestScreenWidthDp / scale).toInt()
                }
            }
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalConfiguration provides config,
            ) {
                content()
            }
        }
    }
}
