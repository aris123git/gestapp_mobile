package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.maquis.caisse.data.local.entity.SaleEntity
import com.maquis.caisse.data.local.entity.SaleItemEntity

@Dao
interface SaleDao {

    @Insert
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert
    suspend fun insertItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: Long): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE sale_id = :saleId ORDER BY id ASC")
    suspend fun getItemsForSale(saleId: Long): List<SaleItemEntity>

    @Transaction
    suspend fun insertSaleWithItems(sale: SaleEntity, items: List<SaleItemEntity>): Long {
        val saleId = insertSale(sale)
        insertItems(items.map { it.copy(saleId = saleId) })
        return saleId
    }
}
