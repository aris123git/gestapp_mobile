package com.maquis.caisse.ui.produits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.theme.GestionBlue
import java.io.File

@Composable
fun ProductTile(
    product: Product,
    imageFile: File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.9f),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GestionBlue.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (imageFile != null) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = GestionBlue.copy(alpha = 0.45f),
                    )
                }
                if (!product.isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextPill("Inactif", PillTone.NEUTRAL)
                    }
                }
            }
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = if (product.hasDetail) {
                    "${MoneyFormat.format(product.detailPrice)} / ${product.detailUnit?.takeIf { it.isNotBlank() } ?: "L"}"
                } else {
                    MoneyFormat.format(product.salePrice)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = GestionBlue,
                fontWeight = FontWeight.Bold,
            )
            if (product.category.isNotBlank()) {
                TextPill(
                    text = product.category,
                    tone = PillTone.CYAN,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
