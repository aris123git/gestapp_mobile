package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room d'un produit du catalogue.
 *
 * [imagePath] est un chemin relatif sous le stockage privé de l'app
 * (ex: `product_images/abc.jpg`), jamais une Uri Android (`content://`…).
 * Les prix sont en FCFA entiers.
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val category: String,
    @ColumnInfo(name = "sale_price")
    val salePrice: Long,
    @ColumnInfo(name = "purchase_price")
    val purchasePrice: Long,
    val stock: Int,
    @ColumnInfo(name = "alert_threshold")
    val alertThreshold: Int,
    @ColumnInfo(name = "image_path")
    val imagePath: String?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "track_stock", defaultValue = "1")
    val trackStock: Boolean = true,
    @ColumnInfo(name = "stock_open", defaultValue = "0")
    val stockOpen: Double = 0.0,
    @ColumnInfo(name = "content_per_unit", defaultValue = "0")
    val contentPerUnit: Double = 0.0,
    @ColumnInfo(name = "detail_price", defaultValue = "0")
    val detailPrice: Long = 0L,
    @ColumnInfo(name = "detail_unit")
    val detailUnit: String? = null,
    @ColumnInfo(name = "stock_unit")
    val stockUnit: String? = null,
)
