package com.maquis.caisse.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BeneficeStatsTest {
    @Test
    fun dashboardStats_expensesLowerCaAndBeneficeMargin() {
        // Ventes 10 000, coût 4 000, dépenses 1 000 → CA 9 000, bénéfice 5 000, marge 50 % sur ventes
        val stats = DashboardStats(
            ordersToday = 2,
            openOrders = 0,
            caGenerated = 9_000L,
            caCollected = 9_000L,
            toCollect = 0L,
            costOfGoods = 4_000L,
            benefice = 5_000L,
            expensesTotal = 1_000L,
            topProducts = emptyList(),
            topCategories = emptyList(),
            caisseDuJour = CaisseDuJour(
                cashToday = 0L,
                mobileToday = 0L,
                debtToday = 0L,
                avoirToday = 0L,
            ),
        )
        assertEquals(5_000L, stats.benefice)
        assertEquals(50, stats.marginPercent)
    }

    @Test
    fun dashboardStats_marginAndBenefice() {
        val stats = DashboardStats(
            ordersToday = 2,
            openOrders = 0,
            caGenerated = 10_000L,
            caCollected = 10_000L,
            toCollect = 0L,
            costOfGoods = 4_000L,
            benefice = 6_000L,
            topProducts = emptyList(),
            topCategories = emptyList(),
            caisseDuJour = CaisseDuJour(
                cashToday = 0L,
                mobileToday = 0L,
                debtToday = 0L,
                avoirToday = 0L,
            ),
        )
        assertEquals(6_000L, stats.benefice)
        assertEquals(60, stats.marginPercent)
    }

    @Test
    fun productSalesRow_beneficeFromCost() {
        val row = ProductSalesRow(
            productName = "Attieke",
            categoryName = "Plats",
            quantity = 3,
            revenue = 3_000L,
            cost = 1_200L,
        )
        assertEquals(1_800L, row.benefice)
    }

    @Test
    fun marginPercent_zeroWhenNoRevenue() {
        val stats = DashboardStats(
            ordersToday = 0,
            openOrders = 0,
            caGenerated = 0L,
            caCollected = 0L,
            toCollect = 0L,
            costOfGoods = 0L,
            benefice = 0L,
            topProducts = emptyList(),
            topCategories = emptyList(),
            caisseDuJour = CaisseDuJour(
                cashToday = 0L,
                mobileToday = 0L,
                debtToday = 0L,
                avoirToday = 0L,
            ),
        )
        assertEquals(0, stats.marginPercent)
    }
}
