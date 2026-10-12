package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dette_paiements",
    foreignKeys = [
        ForeignKey(
            entity = DetteEntity::class,
            parentColumns = ["id"],
            childColumns = ["dette_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["dette_id"]), Index(value = ["paid_at"])],
)
data class DettePaiementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "dette_id") val detteId: Long,
    val amount: Long,
    @ColumnInfo(name = "paid_at") val paidAt: Long,
    @ColumnInfo(name = "user_id") val userId: Long? = null,
    @ColumnInfo(name = "user_name") val userName: String = "",
    val note: String = "",
)
