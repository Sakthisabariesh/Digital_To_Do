package com.mindecho.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// High-Density Color Palette
private val ActivePillBackground = Color(0xFF2C2C2E)
private val ActivePillBorder = Color(0xFF64B5F6)
private val InactivePillBackground = Color(0xFF141416)
private val InactivePillBorder = Color(0xFF1E1E22)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF757575)
private val AccentDot = Color(0xFF64B5F6)

/**
 * Ergonomic rolling 6-day horizontal date strip [Today down to D-5].
 */
@Composable
fun DateStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Generate the 6-day rolling cycle list (Today, Yesterday, D-2, D-3, D-4, D-5)
    val dates = remember {
        val today = LocalDate.now()
        (0L..5L).map { offset -> today.minusDays(offset) }
    }

    val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dates.forEach { date ->
            val isSelected = date == selectedDate
            val isToday = date == LocalDate.now()
            val isYesterday = date == LocalDate.now().minusDays(1)

            val label = when {
                isToday -> "TODAY"
                isYesterday -> "YESTERDAY"
                else -> date.format(dayFormatter).uppercase(Locale.getDefault())
            }

            val dateNumber = date.format(monthDayFormatter)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) ActivePillBackground else InactivePillBackground)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) ActivePillBorder else InactivePillBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onDateSelected(date) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) AccentDot else TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = dateNumber,
                        color = if (isSelected) TextWhite else TextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
