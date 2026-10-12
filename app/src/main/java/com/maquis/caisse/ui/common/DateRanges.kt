package com.maquis.caisse.ui.common

import com.maquis.caisse.domain.model.StatsPeriod
import java.util.Calendar

object DateRanges {
    fun dayBounds(calendar: Calendar = Calendar.getInstance()): Pair<Long, Long> {
        val start = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return start to end
    }

    fun todayBounds(): Pair<Long, Long> = dayBounds()

    fun yesterdayBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return dayBounds(cal)
    }

    fun lastDaysBounds(days: Int): Pair<Long, Long> {
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(days - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return start to end
    }

    fun allTimeBounds(): Pair<Long, Long> = 0L to Long.MAX_VALUE

    fun boundsFor(
        period: StatsPeriod,
        customDayMs: Long? = null,
        customFromMs: Long? = null,
        customToMs: Long? = null,
    ): Pair<Long, Long> = when (period) {
        StatsPeriod.TODAY -> todayBounds()
        StatsPeriod.YESTERDAY -> yesterdayBounds()
        StatsPeriod.WEEK -> lastDaysBounds(7)
        StatsPeriod.MONTH -> lastDaysBounds(30)
        StatsPeriod.CUSTOM_DAY -> {
            val day = customDayMs ?: System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = day }
            dayBounds(cal)
        }
        StatsPeriod.CUSTOM_RANGE -> {
            val from = customFromMs ?: todayBounds().first
            val to = customToMs ?: todayBounds().second
            val toCal = Calendar.getInstance().apply { timeInMillis = maxOf(from, to) }
            val endOfTo = dayBounds(toCal).second
            val fromCal = Calendar.getInstance().apply { timeInMillis = minOf(from, to) }
            val startOfFrom = dayBounds(fromCal).first
            startOfFrom to endOfTo
        }
        StatsPeriod.ALL -> allTimeBounds()
    }
}
