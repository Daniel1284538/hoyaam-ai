package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeadlineDto
import com.example.data.model.HearingDto
import com.example.data.model.MatterDto
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors
import java.text.SimpleDateFormat
import java.util.*

enum class TimelineViewMode(val title: String) {
    PROGRESS_STAGES("مراحل التقاضي"),
    DEADLINES_CHART("مخطط المواعيد الحتمية"),
    MILESTONES("المحطات والقرارات")
}

data class ProceduralStage(
    val key: String,
    val title: String,
    val description: String,
    val order: Int
)

val STANDARD_PROCEDURAL_STAGES = listOf(
    ProceduralStage("filing", "قيد الدعوى", "إيداع صحيفة الدعوى وقيدها بالجدول", 1),
    ProceduralStage("service", "إعلان الخصوم", "تسليم صحيفة الدعوى وإعلان المدعى عليهم", 2),
    ProceduralStage("pleadings", "التحضير والمذكرات", "تبادل المذكرات وحوافظ المستندات", 3),
    ProceduralStage("hearing", "المرافعة والخبراء", "حضور الجلسات ومباشرة أعمال الخبرة", 4),
    ProceduralStage("judgment", "حجز للحكم", "قفل باب المرافعة وتداول الحكم", 5),
    ProceduralStage("appeal", "الطعن والاستئناف", "إجراءات الاستئناف أو النقض", 6),
    ProceduralStage("execution", "التنفيذ", "استلام الصيغة التنفيذية ومباشرة التنفيذ", 7)
)

@Composable
fun LitigationTimelineChart(
    matter: MatterDto,
    deadlines: List<DeadlineDto> = emptyList(),
    hearings: List<HearingDto> = emptyList(),
    onConfirmDeadline: ((String) -> Unit)? = null,
    onProposeDeadlineClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalHoyaamColors.current
    var activeMode by remember { mutableStateOf(TimelineViewMode.PROGRESS_STAGES) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("litigation_timeline_visualizer"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.heroBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = colors.heroText,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "مخطط المسار الإجرائي والمواعيد",
                            color = colors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AmiriFontFamily
                        )
                        Text(
                            text = "تحليل مرئي لحالة التقاضي واستحقاق المهل النظامية",
                            color = colors.textDim,
                            fontSize = 12.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }

                // Summary Badge
                val urgentCount = deadlines.count {
                    val days = calculateDaysUntil(it.computedDueDate)
                    days in 0..3
                }
                if (urgentCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = colors.danger.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.danger)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$urgentCount مواعيد عاجلة",
                                color = colors.danger,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.inset)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TimelineViewMode.values().forEach { mode ->
                    val isSelected = activeMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) colors.card else Color.Transparent)
                            .clickable { activeMode = mode }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.title,
                            color = if (isSelected) colors.accent else colors.text2,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (activeMode) {
                TimelineViewMode.PROGRESS_STAGES -> {
                    ProceduralProgressView(matter = matter)
                }
                TimelineViewMode.DEADLINES_CHART -> {
                    DeadlinesChartTimelineView(
                        deadlines = deadlines,
                        onConfirmDeadline = onConfirmDeadline,
                        onProposeDeadlineClick = onProposeDeadlineClick
                    )
                }
                TimelineViewMode.MILESTONES -> {
                    LitigationMilestonesView(
                        matter = matter,
                        hearings = hearings,
                        deadlines = deadlines
                    )
                }
            }
        }
    }
}

@Composable
private fun ProceduralProgressView(matter: MatterDto) {
    val colors = LocalHoyaamColors.current
    val currentStageKey = matter.stage ?: "filing"
    val stages = STANDARD_PROCEDURAL_STAGES

    val currentStageIndex = stages.indexOfFirst { it.key.equals(currentStageKey, ignoreCase = true) }
        .let { if (it == -1) 1 else it }

    val progressRatio = (currentStageIndex + 1).toFloat() / stages.size.toFloat()
    val animatedProgress by animateFloatAsState(targetValue = progressRatio, label = "stage_progress")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Overall Progress Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.inset,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نسبة التقدم الإجرائي في القضية",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        fontFamily = ArabicSansFontFamily
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                        fontFamily = ArabicSansFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(100.dp)),
                    color = colors.accent,
                    trackColor = colors.border
                )

                Spacer(modifier = Modifier.height(8.dp))

                val activeStage = stages.getOrElse(currentStageIndex) { stages[0] }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "المرحلة الحالية: ${activeStage.title} — ${activeStage.description}",
                        fontSize = 11.sp,
                        color = colors.textDim,
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        }

        // Stepper Visualizer
        Column(modifier = Modifier.fillMaxWidth()) {
            stages.forEachIndexed { index, stage ->
                val isCompleted = index < currentStageIndex
                val isCurrent = index == currentStageIndex
                val isUpcoming = index > currentStageIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Timeline Spine & Node
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCurrent -> colors.heroBg
                                        isCompleted -> colors.good.copy(alpha = 0.15f)
                                        else -> colors.inset
                                    }
                                )
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = when {
                                        isCurrent -> colors.accent
                                        isCompleted -> colors.good
                                        else -> colors.border
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.good,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent)
                                )
                            } else {
                                Text(
                                    text = "${stage.order}",
                                    fontSize = 11.sp,
                                    color = colors.textDim,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (index < stages.size - 1) {
                            Canvas(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(34.dp)
                            ) {
                                drawLine(
                                    color = if (isCompleted) colors.good.copy(alpha = 0.6f) else colors.border,
                                    start = Offset(size.width / 2, 0f),
                                    end = Offset(size.width / 2, size.height),
                                    strokeWidth = 4f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Step Info Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index < stages.size - 1) 8.dp else 0.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) colors.inset else Color.Transparent,
                        border = if (isCurrent) BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)) else null
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stage.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isUpcoming) colors.textDim else colors.text,
                                    fontFamily = ArabicSansFontFamily
                                )

                                if (isCurrent) {
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = colors.accent,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = "المرحلة الحالية",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontFamily = ArabicSansFontFamily
                                        )
                                    }
                                } else if (isCompleted) {
                                    Text(
                                        text = "مكتملة ✓",
                                        fontSize = 11.sp,
                                        color = colors.good,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = ArabicSansFontFamily
                                    )
                                }
                            }

                            Text(
                                text = stage.description,
                                fontSize = 12.sp,
                                color = colors.textDim,
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

@Composable
private fun DeadlinesChartTimelineView(
    deadlines: List<DeadlineDto>,
    onConfirmDeadline: ((String) -> Unit)?,
    onProposeDeadlineClick: (() -> Unit)?
) {
    val colors = LocalHoyaamColors.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (deadlines.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.inset,
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EventAvailable,
                        contentDescription = null,
                        tint = colors.textDim,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "لا توجد مواعيد حتمية مسجلة لهذه القضية حالياً",
                        color = colors.text2,
                        fontSize = 13.sp,
                        fontFamily = ArabicSansFontFamily
                    )
                    if (onProposeDeadlineClick != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onProposeDeadlineClick,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اقتراح ميعاد إجرائي جديد", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Deadline Urgency Breakdown Chart
            val overdue = deadlines.filter { calculateDaysUntil(it.computedDueDate) < 0 }
            val urgent = deadlines.filter { calculateDaysUntil(it.computedDueDate) in 0..3 }
            val upcoming = deadlines.filter { calculateDaysUntil(it.computedDueDate) in 4..14 }
            val later = deadlines.filter { calculateDaysUntil(it.computedDueDate) > 14 }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.inset)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                UrgencyMetricChip(label = "منتهية", count = overdue.size, color = colors.danger)
                UrgencyMetricChip(label = "عاجلة (≤ 3 أيام)", count = urgent.size, color = Color(0xFFD97706))
                UrgencyMetricChip(label = "خلال أسبوعين", count = upcoming.size, color = colors.accent)
                UrgencyMetricChip(label = "مستقبلية", count = later.size, color = colors.good)
            }

            // Ordered Deadline Graph Items
            val sortedDeadlines = deadlines.sortedBy { it.computedDueDate }
            sortedDeadlines.forEach { deadline ->
                val daysUntil = calculateDaysUntil(deadline.computedDueDate)
                val isProvisional = deadline.status == "provisional"
                val urgencyColor = when {
                    daysUntil < 0 -> colors.danger
                    daysUntil <= 3 -> Color(0xFFD97706)
                    daysUntil <= 7 -> colors.accent
                    else -> colors.good
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(urgencyColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = deadline.triggerEvent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }

                            // Days Pill
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = urgencyColor.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = when {
                                        daysUntil < 0 -> "متأخر ${-daysUntil} يوم"
                                        daysUntil == 0 -> "اليوم!"
                                        daysUntil == 1 -> "غداً"
                                        else -> "متبقي $daysUntil يوماً"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = urgencyColor,
                                    fontFamily = ArabicSansFontFamily,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = colors.textDim,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تاريخ الاستحقاق: ${deadline.computedDueDate}",
                                    fontSize = 12.sp,
                                    color = colors.text2,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }

                            if (isProvisional) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.warn.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "مبدئي",
                                            fontSize = 11.sp,
                                            color = colors.warn,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (onConfirmDeadline != null) {
                                        Button(
                                            onClick = { onConfirmDeadline(deadline.id) },
                                            shape = RoundedCornerShape(100.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.good),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("اعتماد الميعاد", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.good.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "معتمد نظاماً ✓",
                                        fontSize = 11.sp,
                                        color = colors.good,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

@Composable
private fun LitigationMilestonesView(
    matter: MatterDto,
    hearings: List<HearingDto>,
    deadlines: List<DeadlineDto>
) {
    val colors = LocalHoyaamColors.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Synthesis Info Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.inset,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EventNote,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "سجل الإجراءات والجلسات المترابطة",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        fontFamily = ArabicSansFontFamily
                    )
                    Text(
                        text = "إجمالي ${hearings.size} جلسات و ${deadlines.size} مواعيد إجرائية مقيدة للملف",
                        fontSize = 11.sp,
                        color = colors.textDim,
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        }

        if (hearings.isEmpty() && deadlines.isEmpty()) {
            Text(
                text = "لا توجد جلسات أو مواعيد مضافة بعد.",
                fontSize = 12.sp,
                color = colors.textDim,
                fontFamily = ArabicSansFontFamily,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            // Recent Hearings timeline
            hearings.take(5).forEach { hearing ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "جلسة: ${hearing.sessionDate}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                            if (!hearing.adjournmentReason.isNullOrEmpty()) {
                                Text(
                                    text = hearing.adjournmentReason,
                                    fontSize = 12.sp,
                                    color = colors.text2,
                                    fontFamily = ArabicSansFontFamily,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        if (!hearing.outcome.isNullOrEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = colors.heroBg
                            ) {
                                Text(
                                    text = hearing.outcome,
                                    fontSize = 11.sp,
                                    color = colors.heroText,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontFamily = ArabicSansFontFamily
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
private fun UrgencyMetricChip(
    label: String,
    count: Int,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            fontFamily = ArabicSansFontFamily
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray,
            fontFamily = ArabicSansFontFamily
        )
    }
}

private fun calculateDaysUntil(dateStr: String): Int {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val target = sdf.parse(dateStr) ?: return 0
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val diffMs = target.time - now.time
        (diffMs / (1000 * 60 * 60 * 24)).toInt()
    } catch (e: Exception) {
        0
    }
}
