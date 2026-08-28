package com.example.ui.screens.roll

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HearingDto
import com.example.ui.components.BidiMonoText
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HearingRollScreen(
    dayHearings: List<HearingDto>,
    monthHearingDates: Set<String>,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    onNavigateToMatter: (String) -> Unit
) {
    val colors = LocalHoyaamColors.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val curDate = try { sdf.parse(selectedDate) ?: Date() } catch (e: Exception) { Date() }

    val cal = Calendar.getInstance().apply { time = curDate }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)

    val monthName = SimpleDateFormat("MMMM yyyy", Locale("ar")).format(curDate)

    // Calculate days in month
    val daysInMonthCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = daysInMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = daysInMonthCal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat
    // Shift so Sat = 0: (firstDayOfWeek % 7)
    val leadBlanks = firstDayOfWeek % 7

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "رول الجلسات والأجندة القضائية",
                    color = colors.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )
                Text(
                    text = "جدول جلسات كل القضايا المتاحة لك عبر كل المحاكم",
                    color = colors.textDim,
                    fontSize = 13.sp,
                    fontFamily = ArabicSansFontFamily
                )
            }
        }

        // Calendar Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Month Navigation Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = monthName,
                            color = colors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AmiriFontFamily
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = {
                                    val prevMonthCal = Calendar.getInstance().apply {
                                        time = curDate
                                        add(Calendar.MONTH, -1)
                                    }
                                    onSelectDate(sdf.format(prevMonthCal.time))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "الشهر السابق", tint = colors.text)
                            }

                            TextButton(
                                onClick = { onSelectDate(sdf.format(Date())) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("اليوم", fontSize = 12.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = {
                                    val nextMonthCal = Calendar.getInstance().apply {
                                        time = curDate
                                        add(Calendar.MONTH, 1)
                                    }
                                    onSelectDate(sdf.format(nextMonthCal.time))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "الشهر القادم", tint = colors.text)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Days of week header (Sat -> Fri)
                    val daysHeader = listOf("سبت", "أحد", "إثن", "ثلا", "أرب", "خمي", "جمع")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        daysHeader.forEach { dayName ->
                            Text(
                                text = dayName,
                                color = colors.textDim,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar Days Grid
                    val totalSlots = leadBlanks + daysInMonth
                    val totalRows = (totalSlots + 6) / 7

                    for (row in 0 until totalRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (col in 0 until 7) {
                                val slotIndex = row * 7 + col
                                if (slotIndex < leadBlanks || slotIndex >= totalSlots) {
                                    Spacer(modifier = Modifier.size(36.dp))
                                } else {
                                    val dayNum = slotIndex - leadBlanks + 1
                                    val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayNum)
                                    val isSelected = dateStr == selectedDate
                                    val hasHearings = monthHearingDates.contains(dateStr)

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) colors.accent else Color.Transparent)
                                            .clickable { onSelectDate(dateStr) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$dayNum",
                                                color = if (isSelected) Color.White else colors.text,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || hasHearings) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (hasHearings && !isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(colors.accent, CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        // Agenda for Selected Date
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "أجندة يوم: $selectedDate",
                                color = colors.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AmiriFontFamily
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.inset
                        ) {
                            Text(
                                text = "${dayHearings.size} جلسة",
                                color = colors.textDim,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 12.dp))

                    if (dayHearings.isEmpty()) {
                        Text(
                            text = "لا توجد جلسات مجدولة في هذا اليوم.",
                            color = colors.textDim,
                            fontSize = 13.sp,
                            fontFamily = ArabicSansFontFamily,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            dayHearings.forEach { hearing ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onNavigateToMatter(hearing.matterId) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.inset
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = colors.card,
                                            border = BorderStroke(1.dp, colors.border)
                                        ) {
                                            BidiMonoText(
                                                text = hearing.sessionTime?.take(5) ?: "—",
                                                color = colors.text,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            val matterName = hearing.matters?.matterLabel ?: "قضية"
                                            val courtInfo = listOfNotNull(hearing.matters?.court, hearing.matters?.circuit?.let { "($it)" }).joinToString(" ")
                                            Text(
                                                text = "$matterName — $courtInfo",
                                                color = colors.accent,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = ArabicSansFontFamily
                                            )

                                            if (!hearing.adjournmentReason.isNullOrEmpty()) {
                                                Text(
                                                    text = "قرار الجلسة السابقة: ${hearing.adjournmentReason}",
                                                    color = colors.text,
                                                    fontSize = 12.sp,
                                                    fontFamily = ArabicSansFontFamily,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
