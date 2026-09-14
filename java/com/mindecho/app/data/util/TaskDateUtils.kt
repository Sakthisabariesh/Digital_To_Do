package com.mindecho.app.data.util

import java.time.LocalDate
import java.time.ZoneId

/**
 * Utility functions for calendar-day boundaries and rolling retention windows.
 */
object TaskDateUtils {

    /**
     * Calculates the epoch millisecond timestamp corresponding to the start of the day
     * (00:00:00.000) for the 6th day in the rolling retention window (Today minus 5 full days).
     *
     * Any task created strictly before this cutoff is considered stale and eligible for purge.
     */
    fun getSixDayPurgeCutoff(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return LocalDate.now(zoneId)
            .minusDays(5)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Calculates the start of day (00:00:00.000) in epoch ms for a given date offset (0 = Today, 1 = Yesterday, etc.).
     */
    fun getStartOfDayEpochMs(dayOffsetFromToday: Long = 0L, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return LocalDate.now(zoneId)
            .minusDays(dayOffsetFromToday)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Calculates the end of day (23:59:59.999) in epoch ms for a given date offset.
     */
    fun getEndOfDayEpochMs(dayOffsetFromToday: Long = 0L, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return LocalDate.now(zoneId)
            .minusDays(dayOffsetFromToday)
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli() - 1L
    }
}
