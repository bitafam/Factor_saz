package com.example.util

import java.util.Calendar

object JalaliCalendar {
    
    /**
     * Get today's shamsi date as yyyy/MM/dd format (e.g., 1405/03/29)
     */
    fun getTodayJalali(): String {
        val cal = Calendar.getInstance()
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Mathematically precise and lightweight Jalali date algorithm helper
     */
    fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): String {
        var gy = gYear - 1600
        var gm = gMonth - 1
        var gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) {
            gDayNo += when (i) {
                0 -> 31
                1 -> if ((gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0)) 29 else 28
                2 -> 31
                3 -> 30
                4 -> 31
                5 -> 30
                6 -> 31
                7 -> 31
                8 -> 30
                9 -> 31
                10 -> 30
                else -> 0
            }
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        var jd = 0
        if (jDayNo < 186) {
            jm = 1 + jDayNo / 31
            jd = 1 + jDayNo % 31
        } else {
            val jDayNoAdjusted = jDayNo - 186
            jm = 7 + jDayNoAdjusted / 30
            jd = 1 + jDayNoAdjusted % 30
        }

        val yStr = jy.toString()
        val mStr = if (jm < 10) "0$jm" else jm.toString()
        val dStr = if (jd < 10) "0$jd" else jd.toString()
        return "$yStr/$mStr/$dStr"
    }
}
