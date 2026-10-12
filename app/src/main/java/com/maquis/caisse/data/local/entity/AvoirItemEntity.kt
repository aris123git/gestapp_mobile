package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "avoir_items",
    foreignKeys = [
        ForeignKey(
            entity = AvoirEntity::class,
            parentColumns = ["id"],
            childColumns = ["avoir_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["avoir_id"]), Index(value = ["product_id"])],
)
data class AvoirItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "avoir_id") val avoirId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "unit_price") val unitPrice: Long,
    val quantity: Int,
    @ColumnInfo(name = "line_total") val lineTotal: Long,
)
