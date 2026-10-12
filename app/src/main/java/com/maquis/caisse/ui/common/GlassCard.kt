package com.maquis.caisse.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.GestionCyan
import com.maquis.caisse.ui.theme.GestionDanger
import com.maquis.caisse.ui.theme.GestionSuccess
import com.maquis.caisse.ui.theme.GestionWarning

enum class PillTone {
    INFO,
    SUCCESS,
    WARNING,
    DANGER,
    NEUTRAL,
    CYAN,
}

/**
 * Pastille texte style Assistant (ex. « Urgent · Stock »).
 */
@Composable
fun TextPill(
    text: String,
    tone: PillTone = PillTone.INFO,
    modifier: Modifier = Modifier,
) {
    val bg = when (tone) {
        PillTone.INFO -> GestionBlue.copy(alpha = 0.12f)
        PillTone.SUCCESS -> GestionSuccess.copy(alpha = 0.14f)
        PillTone.WARNING -> GestionWarning.copy(alpha = 0.18f)
        PillTone.DANGER -> GestionDanger.copy(alpha = 0.14f)
        PillTone.NEUTRAL -> Color(0xFF64748B).copy(alpha = 0.12f)
        PillTone.CYAN -> GestionCyan.copy(alpha = 0.16f)
    }
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Carte « verre » style Assistant — à réutiliser sur tous les écrans.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.White.copy(alpha = 0.9f),
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    } else {
        Surface(
            shape = shape,
            color = Color.White.copy(alpha = 0.9f),
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = GestionBlue)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, enabled = actionEnabled) {
                Text(actionLabel)
            }
        }
    }
}
