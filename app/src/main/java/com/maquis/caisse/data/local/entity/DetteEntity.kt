package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dettes",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["status"]),
        Index(value = ["order_id"]),
    ],
)
data class DetteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "customer_name") val customerName: String,
    @ColumnInfo(name = "customer_phone") val customerPhone: String = "",
    @ColumnInfo(name = "order_id") val orderId: Long? = null,
    @ColumnInfo(name = "order_public_id") val orderPublicId: String? = null,
    @ColumnInfo(name = "original_amount") val originalAmount: Long,
    @ColumnInfo(name = "paid_amount") val paidAmount: Long = 0L,
    /** OPEN | PARTIAL | SETTLED */
    val status: String = "OPEN",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "user_id") val userId: Long? = null,
    @ColumnInfo(name = "user_name") val userName: String = "",
    val note: String = "",
)
