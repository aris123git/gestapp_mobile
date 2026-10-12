package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["created_at"]),
        Index(value = ["user_id"]),
    ],
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "product_name") val productName: String,
    /** ENTREE | SORTIE | VENTE | PERTE | INVENTAIRE | AVOIR | CORRECTION | AJUSTEMENT_AUTORISE */
    val type: String,
    val quantity: Int,
    @ColumnInfo(name = "previous_stock") val previousStock: Int,
    @ColumnInfo(name = "new_stock") val newStock: Int,
    val motif: String = "",
    val supplier: String? = null,
    val comment: String? = null,
    @ColumnInfo(name = "user_id") val userId: Long? = null,
    @ColumnInfo(name = "user_name") val userName: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
