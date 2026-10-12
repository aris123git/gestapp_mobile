package com.maquis.caisse.domain.order

import java.util.Calendar

/**
 * ID lisible HHMMDDMMYYYY, avec suffixe -01, -02… en cas de collision à la minute.
 */
object OrderPublicId {
    fun baseFromEpoch(epochMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val hh = cal.get(Calendar.HOUR_OF_DAY)
        val mm = cal.get(Calendar.MINUTE)
        val dd = cal.get(Calendar.DAY_OF_MONTH)
        val mo = cal.get(Calendar.MONTH) + 1
        val yyyy = cal.get(Calendar.YEAR)
        return "%02d%02d%02d%02d%04d".format(hh, mm, dd, mo, yyyy)
    }

    fun allocate(base: String, existingCount: Int): String {
        if (existingCount <= 0) return base
        return "$base-%02d".format(existingCount)
    }
}
