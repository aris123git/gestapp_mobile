package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "caisse_sessions",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["opened_at"]),
        Index(value = ["handoff_status"]),
    ],
)
data class CaisseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "user_name") val userName: String,
    @ColumnInfo(name = "opened_at") val openedAt: Long,
    @ColumnInfo(name = "closed_at") val closedAt: Long? = null,
    /** Fond de caisse saisi à l'ouverture (espèces en caisse au départ). */
    @ColumnInfo(name = "opening_balance") val openingBalance: Long = 0L,
    /** Nombre de commandes payées pendant la session. */
    @ColumnInfo(name = "sales_count") val salesCount: Int = 0,
    /** Montant total FCFA encaissé pendant la session. */
    @ColumnInfo(name = "total_amount") val totalAmount: Long = 0L,
    /** Ventilation espèces. */
    @ColumnInfo(name = "cash_sales") val cashSales: Long = 0L,
    /** Ventilation mobile money (Orange, Moov, Wave, Carte). */
    @ColumnInfo(name = "mobile_sales") val mobileSales: Long = 0L,
    /** Ventilation dettes. */
    @ColumnInfo(name = "debt_sales") val debtSales: Long = 0L,
    /** Espèces réellement comptées à la clôture (null = pas encore compté). */
    @ColumnInfo(name = "cash_counted") val cashCounted: Long? = null,
    /**
     * Passation de relève : null tant que la session est ouverte ;
     * PENDING / VALIDATED / ANOMALY après clôture.
     */
    @ColumnInfo(name = "handoff_status") val handoffStatus: String? = null,
    @ColumnInfo(name = "validated_by_user_id") val validatedByUserId: Long? = null,
    @ColumnInfo(name = "validated_by_name") val validatedByName: String? = null,
    @ColumnInfo(name = "validated_at") val validatedAt: Long? = null,
    @ColumnInfo(name = "anomaly_note") val anomalyNote: String? = null,
)
