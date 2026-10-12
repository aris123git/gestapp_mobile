package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "avoirs",
    indices = [Index(value = ["created_at"]), Index(value = ["order_id"])],
)
data class AvoirEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "order_id") val orderId: Long? = null,
    @ColumnInfo(name = "order_public_id") val orderPublicId: String? = null,
    @ColumnInfo(name = "customer_name") val customerName: String = "",
    val reason: String,
    val amount: Long,
    /** CASH = montant libre ; PRODUCT = lignes produits. */
    @ColumnInfo(name = "avoir_type", defaultValue = "CASH") val avoirType: String = "CASH",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "user_id") val userId: Long? = null,
    @ColumnInfo(name = "user_name") val userName: String = "",
    val note: String = "",
)
