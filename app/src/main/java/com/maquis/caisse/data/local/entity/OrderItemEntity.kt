package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["product_id"]),
        Index(value = ["category_name"]),
    ],
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "order_id") val orderId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "category_name") val categoryName: String = "",
    @ColumnInfo(name = "unit_price") val unitPrice: Long,
    val quantity: Int,
    @ColumnInfo(name = "line_total") val lineTotal: Long,
    @ColumnInfo(name = "unit_fraction", defaultValue = "1") val unitFraction: Double = 1.0,
)
