package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["name"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** PIN simple (maquis) — hash léger ou clair pour offline local. */
    val pin: String,
    /** ADMIN | MANAGER | CAISSIER | SERVEUSE */
    val role: String,
    /** Permissions séparées par virgule. */
    val permissions: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    /** Colonne conservée (ancienne option « serveuse »), plus utilisée. */
    @ColumnInfo(name = "is_waitress") val isWaitress: Boolean = false,
)
