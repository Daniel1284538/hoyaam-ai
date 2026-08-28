package com.example.ui.screens.deadlines

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeadlineDto
import com.example.ui.components.BidiMonoText
import com.example.ui.components.ConfirmedChip
import com.example.ui.components.DangerChip
import com.example.ui.components.ProvisionalChip
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors
import com.example.util.CalendarSyncManager
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DeadlinesListScreen(
    deadlines: List<DeadlineDto>,
    mattersMap: Map<String, String> = emptyMap(),
    isLoading: Boolean,
    onConfirmDeadline: (String) -> Unit,
    onNavigateToMatter: (String) -> Unit,
    onSyncDeadlineToCalendar: ((DeadlineDto) -> CalendarSyncManager.SyncResult)? = null,
    onSyncAllDeadlines: (() -> Pair<Int, Int>)? = null
) {
    val colors = LocalHoyaamColors.current
    val context = LocalContext.current
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayDate = sdf.parse(todayStr) ?: Date()

    var showSyncSuccessDialog by remember { mutableStateOf<String?>(null) }

    // Calendar permissions launcher
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            val res = onSyncAllDeadlines?.invoke()
            if (res != null) {
                Toast.makeText(
                    context,
                    "تمت مزامنة ${res.first} ميعاد مع تقويم الجهاز بنجاح!",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(
                context,
                "إذن التقويم مطلوب لمزامنة المواعيد مع تقويم الجهاز",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val overdue = mutableListOf<Pair<DeadlineDto, Int>>()
    val thisWeek = mutableListOf<Pair<DeadlineDto, Int>>()
    val upcoming = mutableListOf<Pair<DeadlineDto, Int>>()

    deadlines.forEach { d ->
        val dDate = try { sdf.parse(d.computedDueDate) } catch (e: Exception) { null }
        val days = if (dDate != null) ((dDate.time - todayDate.time) / (1000 * 60 * 60 * 24)).toInt() else 0

        when {
            d.computedDueDate < todayStr -> overdue.add(d to days)
            days <= 7 -> thisWeek.add(d to days)
            else -> upcoming.add(d to days)
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.text)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "حساب ومراقبة المواعيد الإجرائية",
                            color = colors.text,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AmiriFontFamily
                        )
                        Text(
                            text = "مواعيد الدعاوى القضائية مع إمكانية المزامنة مع تقويم الجهاز والتذكير",
                            color = colors.textDim,
                            fontSize = 12.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                    }

                    // Sync All Button
                    Button(
                        onClick = {
                            if (CalendarSyncManager.hasCalendarPermission(context)) {
                                val res = onSyncAllDeadlines?.invoke()
                                if (res != null) {
                                    showSyncSuccessDialog = "تمت مزامنة ${res.first} ميعاد إجرائي مع تقويم الجهاز، مع ضبط منبهات التنبيه التلقائية."
                                }
                            } else {
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("sync_all_calendar_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مزامنة التقويم", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Push Notification / Calendar Notice Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.heroBg,
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = colors.heroText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "يتم حفظ المواعيد محلياً في قاعدة البيانات ومزامنتها مع تقويم الهاتف لتلقي إشعارات تنبيهية قبل 3 أيام، و24 ساعة، وساعتين من استحقاق الموعد.",
                            color = colors.heroText,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }
            }
        }

        // Section 1: Overdue Deadlines
        item {
            DeadlineGroupSectionWithSync(
                title = "مواعيد متأخرة",
                items = overdue,
                badgeColor = colors.danger,
                mattersMap = mattersMap,
                onConfirmDeadline = onConfirmDeadline,
                onNavigateToMatter = onNavigateToMatter,
                onSyncToCalendar = { d ->
                    handleSingleDeadlineSync(context, d, mattersMap[d.matterId] ?: "قضية", onSyncDeadlineToCalendar)
                }
            )
        }

        // Section 2: This Week's Deadlines
        item {
            DeadlineGroupSectionWithSync(
                title = "مواعيد هذا الأسبوع",
                items = thisWeek,
                badgeColor = colors.warn,
                mattersMap = mattersMap,
                onConfirmDeadline = onConfirmDeadline,
                onNavigateToMatter = onNavigateToMatter,
                onSyncToCalendar = { d ->
                    handleSingleDeadlineSync(context, d, mattersMap[d.matterId] ?: "قضية", onSyncDeadlineToCalendar)
                }
            )
        }

        // Section 3: Upcoming Deadlines
        item {
            DeadlineGroupSectionWithSync(
                title = "مواعيد قادمة",
                items = upcoming,
                badgeColor = colors.good,
                mattersMap = mattersMap,
                onConfirmDeadline = onConfirmDeadline,
                onNavigateToMatter = onNavigateToMatter,
                onSyncToCalendar = { d ->
                    handleSingleDeadlineSync(context, d, mattersMap[d.matterId] ?: "قضية", onSyncDeadlineToCalendar)
                }
            )
        }
    }

    // Success dialog
    showSyncSuccessDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { showSyncSuccessDialog = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.good, modifier = Modifier.size(36.dp)) },
            title = { Text("تمت المزامنة بنجاح", fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold) },
            text = { Text(msg, fontSize = 13.sp, fontFamily = ArabicSansFontFamily) },
            confirmButton = {
                Button(onClick = { showSyncSuccessDialog = null }) {
                    Text("حسناً")
                }
            }
        )
    }
}

private fun handleSingleDeadlineSync(
    context: Context,
    deadline: DeadlineDto,
    matterLabel: String,
    onSyncDeadlineToCalendar: ((DeadlineDto) -> CalendarSyncManager.SyncResult)?
) {
    if (CalendarSyncManager.hasCalendarPermission(context) && onSyncDeadlineToCalendar != null) {
        val res = onSyncDeadlineToCalendar(deadline)
        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
    } else {
        // Fallback to Intent
        try {
            val intent = CalendarSyncManager.createDeadlineCalendarIntent(deadline, matterLabel)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح تطبيق التقويم: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun DeadlineGroupSectionWithSync(
    title: String,
    items: List<Pair<DeadlineDto, Int>>,
    badgeColor: Color,
    mattersMap: Map<String, String>,
    onConfirmDeadline: (String) -> Unit,
    onNavigateToMatter: (String) -> Unit,
    onSyncToCalendar: (DeadlineDto) -> Unit
) {
    val colors = LocalHoyaamColors.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = colors.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AmiriFontFamily
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${items.size}",
                    color = badgeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        if (items.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.card,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "لا توجد مواعيد في هذه الفئة.",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp),
                    fontFamily = ArabicSansFontFamily
                )
            }
        } else {
            items.forEach { (deadline, daysDiff) ->
                val matterName = mattersMap[deadline.matterId] ?: "قضية #${deadline.matterId.take(6)}"
                val isProvisional = deadline.status == "provisional"

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, if (isProvisional) colors.border else colors.good.copy(alpha = 0.4f)),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToMatter(deadline.matterId) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = matterName,
                                color = colors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArabicSansFontFamily,
                                modifier = Modifier.weight(1f)
                            )
                            if (isProvisional) {
                                ProvisionalChip(text = "مقترح")
                            } else {
                                ConfirmedChip(text = "مؤكد")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = deadline.triggerEvent,
                            color = colors.text2,
                            fontSize = 13.sp,
                            fontFamily = ArabicSansFontFamily
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("الاستحقاق:", color = colors.textDim, fontSize = 12.sp)
                                BidiMonoText(
                                    text = deadline.computedDueDate,
                                    color = if (daysDiff < 0) colors.danger else colors.text,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = when {
                                        daysDiff < 0 -> "(متأخر ${-daysDiff} يوم)"
                                        daysDiff == 0 -> "(اليوم)"
                                        daysDiff == 1 -> "(غداً)"
                                        else -> "(باقي $daysDiff يوم)"
                                    },
                                    color = if (daysDiff < 0) colors.danger else if (daysDiff <= 7) colors.warn else colors.good,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Add to Calendar Button
                                IconButton(
                                    onClick = { onSyncToCalendar(deadline) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.CalendarMonth,
                                        contentDescription = "إضافة للتقويم",
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (isProvisional) {
                                    Button(
                                        onClick = { onConfirmDeadline(deadline.id) },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.good),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("تأكيد", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
