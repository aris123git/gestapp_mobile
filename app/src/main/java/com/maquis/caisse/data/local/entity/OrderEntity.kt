package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["public_id"], unique = true),
        Index(value = ["status"]),
        Index(value = ["created_at"]),
        Index(value = ["waitress_id"]),
        Index(value = ["table_id"]),
    ],
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** ID lisible HHMMDDMMYYYY ou HHMMDDMMYYYY-NN */
    @ColumnInfo(name = "public_id") val publicId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** EN_COURS | NON_PAYEE | PAYEE | ANNULEE */
    val status: String,
    @ColumnInfo(name = "table_id") val tableId: Long? = null,
    @ColumnInfo(name = "table_label") val tableLabel: String? = null,
    @ColumnInfo(name = "waitress_id") val waitressId: Long? = null,
    @ColumnInfo(name = "waitress_name") val waitressName: String? = null,
    @ColumnInfo(name = "total_amount") val totalAmount: Long = 0L,
    @ColumnInfo(name = "paid_amount") val paidAmount: Long = 0L,
    val note: String? = null,
    @ColumnInfo(name = "created_by_user_id") val createdByUserId: Long? = null,
    @ColumnInfo(name = "created_by_name") val createdByName: String? = null,
)
