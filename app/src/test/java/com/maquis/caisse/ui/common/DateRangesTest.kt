package com.maquis.caisse.ui.common

import com.maquis.caisse.domain.model.StatsPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateRangesTest {
    @Test
    fun todayBounds_isSameCalendarDay() {
        val (from, to) = DateRanges.todayBounds()
        assertTrue(from <= to)
        val start = Calendar.getInstance().apply { timeInMillis = from }
        val end = Calendar.getInstance().apply { timeInMillis = to }
        assertEquals(start.get(Calendar.DAY_OF_YEAR), end.get(Calendar.DAY_OF_YEAR))
        assertEquals(0, start.get(Calendar.HOUR_OF_DAY))
        assertEquals(23, end.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun customRange_normalizesFromStartToEndOfDay() {
        val day = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 10, 15, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val next = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 12, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val (from, to) = DateRanges.boundsFor(
            StatsPeriod.CUSTOM_RANGE,
            customFromMs = next,
            customToMs = day,
        )
        assertTrue(from < to)
        val fromCal = Calendar.getInstance().apply { timeInMillis = from }
        val toCal = Calendar.getInstance().apply { timeInMillis = to }
        assertEquals(10, fromCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, fromCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(12, toCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, toCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun weekBounds_coversSevenDays() {
        val (from, to) = DateRanges.boundsFor(StatsPeriod.WEEK)
        val spanDays = ((to - from) / (24L * 60L * 60L * 1000L)) + 1
        assertTrue(spanDays in 7L..8L)
    }
}
