package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["user_id"]),
        Index(value = ["category"]),
    ],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val description: String,
    /** Montant en FCFA entiers (pas de REAL). */
    val amount: Long,
    val category: String? = null,
    @ColumnInfo(name = "user_id") val userId: Long? = null,
    @ColumnInfo(name = "user_name") val userName: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
