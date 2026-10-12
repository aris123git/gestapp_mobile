package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "total_amount")
    val totalAmount: Long,
    @ColumnInfo(name = "payment_mode")
    val paymentMode: String,
    @ColumnInfo(name = "cash_amount")
    val cashAmount: Long,
    @ColumnInfo(name = "mobile_money_amount")
    val mobileMoneyAmount: Long,
    @ColumnInfo(name = "voucher_amount")
    val voucherAmount: Long,
    @ColumnInfo(name = "debt_amount")
    val debtAmount: Long,
    @ColumnInfo(name = "amount_tendered")
    val amountTendered: Long,
    @ColumnInfo(name = "change_amount")
    val changeAmount: Long,
)
