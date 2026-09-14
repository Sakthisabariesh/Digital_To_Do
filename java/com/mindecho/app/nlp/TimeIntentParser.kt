package com.mindecho.app.nlp

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

/**
 * Result data class for offline natural language time and intent extraction.
 */
data class ParsedTaskIntent(
    val rawSentence: String,
    val cleanedTitle: String,
    val triggerTimestamp: Long = 0L,
    val hasScheduledTime: Boolean = false,
    val matchedTimeExpression: String? = null
)

/**
 * Deterministic, 100% offline Natural Language Time & Intent Parser for MindEcho.
 *
 * Designed to execute in sub-millisecond time on Snapdragon 8 Gen 3 without any network calls.
 * Extracts relative durations, calendar offsets, and clock targets while sanitizing reminder prefixes.
 */
object TimeIntentParser {

    private val PREFIX_PATTERNS = listOf(
        Pattern.compile("^(?:please\\s+)?(?:remind\\s+me\\s+to|remind\\s+me|remember\\s+to|note\\s+to|task\\s+to|schedule\\s+a\\s+reminder\\s+to|schedule\\s+to|schedule|set\\s+a\\s+reminder\\s+for|set\\s+a\\s+reminder\\s+to|set\\s+reminder\\s+to|set\\s+reminder\\s+for|create\\s+a\\s+task\\s+to|create\\s+task\\s+to|alert\\s+me\\s+to|notify\\s+me\\s+to|don't\\s+forget\\s+to|dont\\s+forget\\s+to)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(?:hey\\s+mindecho|mindecho|assistant|alexa|siri|google)\\s*[,:]?\\s*", Pattern.CASE_INSENSITIVE)
    )

    // Relative duration: "in 5 minutes", "after 15 mins", "in 2 hours", "in an hour", "in half an hour"
    private val RELATIVE_DURATION_PATTERN = Pattern.compile(
        "\\b(?:in|after)\\s+(?:(half\\s+an?)|(an?)|(\\d+(?:\\.\\d+)?))\\s*(second|seconds|sec|secs|minute|minutes|min|mins|hour|hours|hr|hrs)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Day offset with optional clock time: "today at 6pm", "tomorrow at 10:30 am", "day after tomorrow at 4pm", "tonight at 8pm"
    private val DAY_OFFSET_WITH_TIME_PATTERN = Pattern.compile(
        "\\b(day\\s+after\\s+tomorrow|tomorrow|today|tonight|this\\s+evening|this\\s+afternoon|this\\s+morning)(?:\\s+(?:at|around|by)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?)?\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Absolute standalone clock target: "at 6pm", "at 10:30 am", "at 18:00", "by 4 pm"
    private val STANDALONE_TIME_PATTERN = Pattern.compile(
        "\\b(?:at|by|around)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses the voice transcript and extracts scheduling intent and cleaned task title.
     *
     * @param rawSentence The spoken voice string (e.g., "Remind me to call John tomorrow at 10:30 am")
     * @param referenceTime Reference [ZonedDateTime] (defaults to current system time)
     * @param zoneId The user's active [ZoneId]
     * @return [ParsedTaskIntent] containing sanitized title and target epoch millisecond timestamp.
     */
    fun parse(
        rawSentence: String,
        referenceTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ParsedTaskIntent {
        val trimmed = rawSentence.trim()
        if (trimmed.isEmpty()) {
            return ParsedTaskIntent(
                rawSentence = "",
                cleanedTitle = "",
                triggerTimestamp = 0L,
                hasScheduledTime = false
            )
        }

        var matchedExpression: String? = null
        var computedTargetZdt: ZonedDateTime? = null

        // 1. Check relative duration ("in 5 minutes", "after 10 mins", "in 2 hours")
        val relMatcher = RELATIVE_DURATION_PATTERN.matcher(trimmed)
        if (relMatcher.find()) {
            matchedExpression = relMatcher.group(0)
            val isHalf = relMatcher.group(1) != null
            val isSingle = relMatcher.group(2) != null
            val amountStr = relMatcher.group(3)
            val unit = relMatcher.group(4).lowercase(Locale.ROOT)

            val amount: Double = when {
                isHalf -> 0.5
                isSingle -> 1.0
                amountStr != null -> amountStr.toDoubleOrNull() ?: 1.0
                else -> 1.0
            }

            computedTargetZdt = when {
                unit.startsWith("sec") -> referenceTime.plusSeconds(amount.toLong())
                unit.startsWith("min") -> {
                    val seconds = (amount * 60).toLong()
                    referenceTime.plusSeconds(seconds)
                }
                unit.startsWith("h") -> {
                    val seconds = (amount * 3600).toLong()
                    referenceTime.plusSeconds(seconds)
                }
                else -> referenceTime.plusMinutes(5)
            }
        }

        // 2. Check day offset + optional clock time ("today at 6pm", "tomorrow at 10:30 am", "day after tomorrow at 4pm", "tonight at 8pm")
        if (computedTargetZdt == null) {
            val dayMatcher = DAY_OFFSET_WITH_TIME_PATTERN.matcher(trimmed)
            if (dayMatcher.find()) {
                val fullMatch = dayMatcher.group(0)
                val dayKeyword = dayMatcher.group(1).lowercase(Locale.ROOT)
                val hourStr = dayMatcher.group(2)
                val minStr = dayMatcher.group(3)
                val ampm = dayMatcher.group(4)?.lowercase(Locale.ROOT)

                // Calculate base target date
                val targetDate: LocalDate = when {
                    dayKeyword.contains("day after tomorrow") -> referenceTime.toLocalDate().plusDays(2)
                    dayKeyword.contains("tomorrow") -> referenceTime.toLocalDate().plusDays(1)
                    else -> referenceTime.toLocalDate()
                }

                // Determine target time
                val targetTime: LocalTime = if (hourStr != null) {
                    var hour = hourStr.toIntOrNull() ?: 9
                    val min = minStr?.toIntOrNull() ?: 0

                    if (ampm != null) {
                        if (ampm == "pm" && hour < 12) hour += 12
                        if (ampm == "am" && hour == 12) hour = 0
                    } else if (dayKeyword.contains("tonight") || dayKeyword.contains("evening")) {
                        if (hour < 12) hour += 12
                    }

                    LocalTime.of(hour.coerceIn(0, 23), min.coerceIn(0, 59))
                } else {
                    when {
                        dayKeyword.contains("tonight") -> LocalTime.of(20, 0)
                        dayKeyword.contains("evening") -> LocalTime.of(18, 0)
                        dayKeyword.contains("afternoon") -> LocalTime.of(14, 0)
                        dayKeyword.contains("morning") -> LocalTime.of(9, 0)
                        else -> {
                            if (targetDate.isAfter(referenceTime.toLocalDate())) {
                                LocalTime.of(9, 0)
                            } else {
                                referenceTime.toLocalTime().plusHours(1)
                            }
                        }
                    }
                }

                matchedExpression = fullMatch
                var resolvedZdt = ZonedDateTime.of(targetDate, targetTime, zoneId)
                if (resolvedZdt.isBefore(referenceTime) && targetDate == referenceTime.toLocalDate()) {
                    resolvedZdt = resolvedZdt.plusDays(1)
                }
                computedTargetZdt = resolvedZdt
            }
        }

        // 3. Check standalone clock target ("at 6pm", "at 10:30 am", "at 18:00")
        if (computedTargetZdt == null) {
            val standaloneMatcher = STANDALONE_TIME_PATTERN.matcher(trimmed)
            if (standaloneMatcher.find()) {
                matchedExpression = standaloneMatcher.group(0)
                val hourStr = standaloneMatcher.group(1)
                val minStr = standaloneMatcher.group(2)
                val ampm = standaloneMatcher.group(3).lowercase(Locale.ROOT)

                var hour = hourStr.toIntOrNull() ?: 12
                val min = minStr?.toIntOrNull() ?: 0

                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0

                val targetTime = LocalTime.of(hour.coerceIn(0, 23), min.coerceIn(0, 59))
                var targetZdt = ZonedDateTime.of(referenceTime.toLocalDate(), targetTime, zoneId)
                if (targetZdt.isBefore(referenceTime)) {
                    targetZdt = targetZdt.plusDays(1)
                }
                computedTargetZdt = targetZdt
            }
        }

        // 4. Strip timing and boilerplate prefixes to produce clean title
        var cleaned = trimmed

        if (matchedExpression != null) {
            cleaned = cleaned.replace(matchedExpression, "").trim()
        }

        for (pattern in PREFIX_PATTERNS) {
            val matcher = pattern.matcher(cleaned)
            if (matcher.find()) {
                cleaned = cleaned.substring(matcher.end()).trim()
            }
        }

        // Clean up trailing/dangling prepositions
        cleaned = cleaned.replace(Regex("\\b(?:at|on|for|by|in|around)\\s*$", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("^[,:\\s.-]+|[,:\\s.-]+$"), "").trim()

        if (cleaned.isNotEmpty()) {
            cleaned = cleaned.substring(0, 1).uppercase(Locale.getDefault()) + cleaned.substring(1)
        } else {
            cleaned = trimmed
        }

        val triggerEpochMs = computedTargetZdt?.toInstant()?.toEpochMilli() ?: 0L

        return ParsedTaskIntent(
            rawSentence = trimmed,
            cleanedTitle = cleaned,
            triggerTimestamp = triggerEpochMs,
            hasScheduledTime = triggerEpochMs > 0L,
            matchedTimeExpression = matchedExpression
        )
    }

    /**
     * Formats an epoch millisecond timestamp into a human-readable display string.
     * e.g., "Today, 6:00 PM", "Tomorrow, 10:30 AM", "Mon, Sep 14 • 4:00 PM"
     */
    fun formatScheduledTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (epochMs <= 0L) return "No reminder"

        val zdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), zoneId)
        val today = LocalDate.now(zoneId)
        val tomorrow = today.plusDays(1)
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

        return when (zdt.toLocalDate()) {
            today -> "Today, ${zdt.format(timeFormatter)}"
            tomorrow -> "Tomorrow, ${zdt.format(timeFormatter)}"
            else -> {
                val fullFormatter = DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a", Locale.getDefault())
                zdt.format(fullFormatter)
            }
        }
    }
}
