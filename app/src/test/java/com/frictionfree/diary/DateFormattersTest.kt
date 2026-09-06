package com.frictionfree.diary

import com.frictionfree.diary.utils.DateFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateFormattersTest {

    @Test
    fun testStartAndEndOfDay() {
        val now = System.currentTimeMillis()
        val start = DateFormatters.getStartOfDay(now)
        val end = DateFormatters.getEndOfDay(now)

        assertTrue(end > start)
        val calStart = Calendar.getInstance().apply { timeInMillis = start }
        val calEnd = Calendar.getInstance().apply { timeInMillis = end }

        assertEquals(0, calStart.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calStart.get(Calendar.MINUTE))
        assertEquals(0, calStart.get(Calendar.SECOND))

        assertEquals(23, calEnd.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, calEnd.get(Calendar.MINUTE))
        assertEquals(59, calEnd.get(Calendar.SECOND))
    }

    @Test
    fun testRelativeDateToday() {
        val now = System.currentTimeMillis()
        val formatted = DateFormatters.formatRelativeDate(now)
        assertTrue(formatted.startsWith("Today"))
    }
}
