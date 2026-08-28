package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.AlertType
import com.example.data.model.UrgentAlert
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    urgentAlerts: List<UrgentAlert> = emptyList(),
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    currentLang: String,
    onToggleLang: () -> Unit,
    onSignOut: () -> Unit,
    onAlertClick: (UrgentAlert) -> Unit,
    onConfirmAlertDeadline: (String) -> Unit = {}
) {
    val colors = LocalHoyaamColors.current
    var showNotifDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_top_bar"),
        color = colors.bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand / Title info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // هويام mascot as the profile avatar — same illustration used
                // on the auth screens (mascot_hoyaam), replacing the "هـ"
                // initial badge placeholder.
                Image(
                    painter = painterResource(id = R.drawable.mascot_hoyaam),
                    contentDescription = "هويام",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            text = subtitle,
                            color = colors.text2,
                            fontSize = 11.sp,
                            fontFamily = ArabicSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = title,
                        color = colors.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Actions: Notifications Bell, Language Toggle, Theme Toggle, Sign Out
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Notifications Bell with Badge
                Box(
                    modifier = Modifier
                        .testTag("notification_bell_btn")
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.inset)
                        .border(1.dp, colors.borderStrong, RoundedCornerShape(14.dp))
                        .clickable { showNotifDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "التنبيهات العاجلة",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                    if (urgentAlerts.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(18.dp)
                                .background(colors.danger, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${urgentAlerts.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Language Toggle (AR / EN)
                Box(
                    modifier = Modifier
                        .testTag("lang_toggle_btn")
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.inset)
                        .border(1.dp, colors.borderStrong, RoundedCornerShape(14.dp))
                        .clickable { onToggleLang() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentLang == "ar") "EN" else "عربي",
                        color = colors.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Theme Toggle
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .testTag("theme_toggle_btn")
                        .size(42.dp)
                        .background(colors.inset, RoundedCornerShape(14.dp))
                        .border(1.dp, colors.borderStrong, RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = "تبديل النمط",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Sign Out
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .testTag("sign_out_btn")
                        .size(42.dp)
                        .background(colors.inset, RoundedCornerShape(14.dp))
                        .border(1.dp, colors.borderStrong, RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "تسجيل الخروج",
                        tint = colors.danger,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Urgent Alerts Dialog
    if (showNotifDialog) {
        Dialog(onDismissRequest = { showNotifDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔔 التنبيهات العاجلة",
                            color = colors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ArabicSansFontFamily
                        )
                        IconButton(onClick = { showNotifDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = colors.textDim)
                        }
                    }

                    HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 10.dp))

                    if (urgentAlerts.isEmpty()) {
                        Text(
                            text = "لا توجد جلسات أو مواعيد عاجلة خلال الـ ٣ أيام القادمة.",
                            color = colors.textDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 20.dp),
                            fontFamily = ArabicSansFontFamily
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(urgentAlerts) { alert ->
                                val isOverdue = alert.daysUntil < 0
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showNotifDialog = false
                                            onAlertClick(alert)
                                        },
                                    shape = RoundedCornerShape(18.dp),
                                    color = colors.inset,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isOverdue) colors.danger.copy(alpha = 0.6f) else colors.border
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = alert.title,
                                                color = colors.text,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f),
                                                fontFamily = ArabicSansFontFamily
                                            )
                                            if (isOverdue) {
                                                DangerChip(text = "متأخر ${Math.abs(alert.daysUntil)} يوم")
                                            } else if (alert.daysUntil == 0) {
                                                ProvisionalChip(text = "اليوم")
                                            } else {
                                                StageChip(text = "متبقي ${alert.daysUntil} يوم")
                                            }
                                        }

                                        if (!alert.timeOrDetail.isNullOrEmpty()) {
                                            Text(
                                                text = alert.timeOrDetail,
                                                color = colors.textDim,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 4.dp),
                                                fontFamily = ArabicSansFontFamily
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BidiMonoText(
                                                text = alert.date,
                                                color = colors.text2,
                                                fontSize = 12.sp
                                            )

                                            if (alert.isProvisionalDeadline) {
                                                Button(
                                                    onClick = {
                                                        onConfirmAlertDeadline(alert.id)
                                                        showNotifDialog = false
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = colors.accent
                                                    ),
                                                    shape = RoundedCornerShape(100.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("تأكيد الميعاد", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
}
