package com.maquis.caisse.domain.order

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class OrderPublicIdTest {

    @Test
    fun baseFromEpoch_usesHhMmDdMmYyyy() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JULY)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 24)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("202431072026", OrderPublicId.baseFromEpoch(cal.timeInMillis))
    }

    @Test
    fun allocate_noCollision_returnsBase() {
        assertEquals("202431072026", OrderPublicId.allocate("202431072026", 0))
    }

    @Test
    fun allocate_collision_addsSuffix() {
        assertEquals("202431072026-01", OrderPublicId.allocate("202431072026", 1))
        assertEquals("202431072026-02", OrderPublicId.allocate("202431072026", 2))
    }
}
