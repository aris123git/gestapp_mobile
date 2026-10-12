package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_payments",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["order_id"]), Index(value = ["created_at"])],
)
data class OrderPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "order_id") val orderId: Long,
    @ColumnInfo(name = "payment_mode") val paymentMode: String,
    val amount: Long,
    @ColumnInfo(name = "amount_tendered") val amountTendered: Long = 0L,
    @ColumnInfo(name = "change_amount") val changeAmount: Long = 0L,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "user_id") val userId: Long? = null,
    @ColumnInfo(name = "user_name") val userName: String? = null,
)
