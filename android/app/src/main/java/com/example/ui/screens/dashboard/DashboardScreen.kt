package com.example.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeadlineDto
import com.example.data.model.HearingDto
import com.example.data.model.DashboardStatsData
import com.example.ui.components.BidiMonoText
import com.example.ui.components.DangerChip
import com.example.ui.components.ProvisionalChip
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    stats: DashboardStatsData,
    isLoading: Boolean,
    onNavigateToRoll: () -> Unit,
    onNavigateToDeadlines: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToMatter: (String) -> Unit,
    onConfirmDeadline: (String) -> Unit
) {
    val colors = LocalHoyaamColors.current
    val todayFormatted = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(Date())

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Page Title & Subtitle Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "لوحة المتابعة الإجرائية",
                        color = colors.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                    Text(
                        text = todayFormatted,
                        color = colors.textDim,
                        fontSize = 13.sp,
                        fontFamily = ArabicSansFontFamily,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = colors.heroBg,
                    border = BorderStroke(1.dp, colors.heroDecor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colors.accent, CircleShape)
                        )
                        Text(
                            text = "${stats.activeMattersCount} دعوى نشطة",
                            color = colors.heroText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }
            }
        }

        // Sleek Hero Card (Signature Warm Gold container with decorative accents)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.heroBg)
                    .border(1.dp, colors.heroDecor, RoundedCornerShape(30.dp))
                    .padding(20.dp)
            ) {
                // Background decorative circles
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 30.dp, y = 30.dp)
                        .size(150.dp)
                        .border(14.dp, colors.heroDecor.copy(alpha = 0.5f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-5).dp)
                        .size(54.dp)
                        .background(colors.heroDecor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = colors.heroText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "حالة الجلسات والمواعيد الحتمية",
                        color = colors.heroText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ArabicSansFontFamily
                    )

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (stats.overdueDeadlinesCount == 0) "منتظمة ١٠٠%" else "${stats.overdueDeadlinesCount} متأخر",
                            color = if (stats.overdueDeadlinesCount == 0) colors.heroText else colors.danger,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = ArabicSansFontFamily
                        )
                    }

                    Text(
                        text = if (stats.overdueDeadlinesCount == 0)
                            "تم الالتزام بجميع المواعيد الإجرائية المقررة لقانون المرافعات."
                        else
                            "يوجد مواعيد حتمية متأخرة تتطلب تقديم مذكرات أو طعن فوري.",
                        color = colors.heroSub,
                        fontSize = 13.sp,
                        fontFamily = ArabicSansFontFamily,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onNavigateToRoll,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "رول جلسات اليوم",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArabicSansFontFamily
                            )
                        }

                        OutlinedButton(
                            onClick = onNavigateToDeadlines,
                            shape = RoundedCornerShape(100.dp),
                            border = BorderStroke(1.dp, colors.accent),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.accent
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "المواعيد الحتمية",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                }
            }
        }

        // Sleek Stat Tiles Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SleekStatTile(
                        title = "جلسات اليوم",
                        count = stats.todayHearingsCount.toString(),
                        unit = "جلسة",
                        icon = Icons.Outlined.CalendarToday,
                        isAlert = false,
                        onClick = onNavigateToRoll,
                        modifier = Modifier.weight(1f)
                    )
                    SleekStatTile(
                        title = "جلسات الأسبوع",
                        count = stats.weekHearingsCount.toString(),
                        unit = "جلسة",
                        icon = Icons.Outlined.Timer,
                        isAlert = false,
                        onClick = onNavigateToRoll,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SleekStatTile(
                        title = "مواعيد متأخرة",
                        count = stats.overdueDeadlinesCount.toString(),
                        unit = "ميعاد",
                        icon = Icons.Outlined.Alarm,
                        isAlert = stats.overdueDeadlinesCount > 0,
                        onClick = onNavigateToDeadlines,
                        modifier = Modifier.weight(1f)
                    )
                    SleekStatTile(
                        title = "مستندات للمراجعة",
                        count = stats.pendingExtractionsCount.toString(),
                        unit = "مستند",
                        icon = Icons.Outlined.FactCheck,
                        isAlert = false,
                        onClick = onNavigateToReview,
                        modifier = Modifier.weight(1f)
                    )
                }

                SleekStatTile(
                    title = "قوالب الصياغة المعتمدة",
                    count = stats.activeTemplatesCount.toString(),
                    unit = "قالب معتمد",
                    icon = Icons.Outlined.Description,
                    isAlert = false,
                    onClick = onNavigateToTemplates,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Panel 1: Today's Hearings Schedule
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.inset),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "جدول جلسات اليوم",
                                color = colors.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AmiriFontFamily
                            )
                        }

                        TextButton(onClick = onNavigateToRoll) {
                            Text(
                                text = "عرض الكل",
                                color = colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }

                    HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 12.dp))

                    if (stats.todayHearings.isEmpty()) {
                        Text(
                            text = "لا توجد جلسات مقررة اليوم في رول المحكمة.",
                            color = colors.textDim,
                            fontSize = 13.sp,
                            fontFamily = ArabicSansFontFamily,
                            modifier = Modifier.padding(vertical = 14.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            stats.todayHearings.forEach { hearing ->
                                HearingTimelineRow(hearing = hearing, onClick = { onNavigateToMatter(hearing.matterId) })
                            }
                        }
                    }
                }
            }
        }

        // Panel 2: Binding Legal Deadlines (Provisional needing confirmation)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.inset),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Alarm,
                                    contentDescription = null,
                                    tint = colors.warn,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "المواعيد القانونية الحتمية",
                                color = colors.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AmiriFontFamily
                            )
                        }

                        TextButton(onClick = onNavigateToDeadlines) {
                            Text(
                                text = "عرض الكل",
                                color = colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }

                    HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 10.dp))

                    if (stats.topProvisionalDeadlines.isEmpty()) {
                        Text(
                            text = "لا توجد مواعيد مبدئية بانتظار التأكيد.",
                            color = colors.textDim,
                            fontSize = 13.sp,
                            fontFamily = ArabicSansFontFamily,
                            modifier = Modifier.padding(vertical = 14.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            stats.topProvisionalDeadlines.forEach { deadline ->
                                DeadlineCardRow(
                                    deadline = deadline,
                                    onMatterClick = { onNavigateToMatter(deadline.matterId) },
                                    onConfirm = { onConfirmDeadline(deadline.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleekStatTile(
    title: String,
    count: String,
    unit: String,
    icon: ImageVector,
    isAlert: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalHoyaamColors.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .testTag("stat_tile_${title}"),
        shape = RoundedCornerShape(24.dp),
        color = if (isAlert) colors.danger.copy(alpha = 0.08f) else colors.inset,
        border = BorderStroke(1.dp, if (isAlert) colors.danger.copy(alpha = 0.5f) else colors.border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon Badge in Sleek olive-bronze or red
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isAlert) colors.danger else colors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = colors.text2,
                fontSize = 13.sp,
                fontFamily = ArabicSansFontFamily,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                BidiMonoText(
                    text = count,
                    color = if (isAlert) colors.danger else colors.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = unit,
                    color = colors.textDim,
                    fontSize = 11.sp,
                    fontFamily = ArabicSansFontFamily,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
fun HearingTimelineRow(
    hearing: HearingDto,
    onClick: () -> Unit
) {
    val colors = LocalHoyaamColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.inset)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sleek Time Badge
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.borderStrong)
        ) {
            BidiMonoText(
                text = hearing.sessionTime?.take(5) ?: "—",
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val matterName = hearing.matters?.matterLabel ?: "قضية"
            val courtCircuit = listOfNotNull(hearing.matters?.court, hearing.matters?.circuit?.let { "($it)" }).joinToString(" ")
            Text(
                text = "$matterName — $courtCircuit",
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ArabicSansFontFamily
            )

            if (!hearing.adjournmentReason.isNullOrEmpty()) {
                Text(
                    text = "قرار الجلسة السابقة: ${hearing.adjournmentReason}",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    fontFamily = ArabicSansFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = colors.textDim,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun DeadlineCardRow(
    deadline: DeadlineDto,
    onMatterClick: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = LocalHoyaamColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.inset,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = deadline.triggerEvent,
                        color = colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ArabicSansFontFamily
                    )
                    ProvisionalChip(text = "مبدئي")
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = deadline.matters?.matterLabel ?: "",
                        color = colors.text2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onMatterClick() }
                    )
                    Text("•", color = colors.borderStrong)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الاستحقاق: ", color = colors.textDim, fontSize = 12.sp)
                        BidiMonoText(text = deadline.computedDueDate, color = colors.text, fontSize = 12.sp)
                    }
                }
            }

            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("confirm_deadline_${deadline.id}")
            ) {
                Text("تأكيد الميعاد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

