package com.example.ui.screens.corpus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthorityDto
import com.example.ui.components.ConfirmedChip
import com.example.ui.components.DangerChip
import com.example.ui.components.ProvisionalChip
import com.example.ui.components.StageChip
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors

// Must match the DB check constraint (authorities_authority_type_check) —
// "cassation_precedent" and "constitutional" here previously were not
// valid values at all; the real column only allows these four, same as
// the web app's AUTHORITY_TYPE_LABELS.
val SOURCE_TYPE_LABELS = mapOf(
    "statute" to "تشريع",
    "cassation_principle" to "مبدأ نقض",
    "regulation" to "لائحة",
    "fiqh_doctrine" to "فقه إسلامي"
)

@Composable
fun LegalCorpusScreen(
    authorities: List<AuthorityDto>,
    isLoading: Boolean,
    onOpenAddAuthorityDialog: () -> Unit,
    onVerifyAuthority: (String) -> Unit
) {
    val colors = LocalHoyaamColors.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddAuthorityDialog,
                containerColor = colors.text,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_authority_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة مصدر", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        },
        containerColor = colors.bg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "المصادر القانونية المعتمدة",
                        color = colors.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                    Text(
                        text = "قاعدة بيانات أحكام النقض والقوانين المصرية الموثّقة — المصدر الوحيد الذي يستشهد به النموذج",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        fontFamily = ArabicSansFontFamily,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Honest Gap Warning Notice
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.warn.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, colors.warn.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = colors.warn, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "تنبيه الأمانة المهنية: قاعدة بيانات المصادر القانونية تخضع لرقابة بشرية دقيقة. إذا لم تجد نصاً أو سابقة قانونية معينة، يمكنك إضافتها أدناه للتحقق منها وتضمينها فوراً في مذكراتك.",
                            color = colors.warn,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.text)
                    }
                }
            } else if (authorities.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Text(
                            text = "لا توجد مصادر مسجّلة بعد. اضغط «إضافة مصدر» لبدء بناء القاعدة.",
                            color = colors.textDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(20.dp),
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }
            } else {
                items(authorities, key = { it.id }) { authority ->
                    LegalAuthorityCard(
                        authority = authority,
                        onVerify = { onVerifyAuthority(authority.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun LegalAuthorityCard(
    authority: AuthorityDto,
    onVerify: () -> Unit
) {
    val colors = LocalHoyaamColors.current
    val isRepealed = authority.repealedDate != null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.card,
        border = BorderStroke(1.dp, if (isRepealed) colors.danger else colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = authority.title,
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ArabicSansFontFamily,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StageChip(text = SOURCE_TYPE_LABELS[authority.authorityType] ?: authority.authorityType)
                    if (isRepealed) {
                        DangerChip(text = "ملغى / معدّل")
                    } else if (authority.verificationStatus == "human_verified" || authority.verificationStatus == "verified") {
                        ConfirmedChip(text = "موثّق")
                    } else {
                        ProvisionalChip(text = "غير موثّق")
                    }
                }
            }

            Text(
                text = "السند: ${authority.citation}",
                color = colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (!authority.madhhab.isNullOrEmpty()) {
                Text(
                    text = "المذهب / الفرع: ${authority.madhhab}",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (authority.verificationStatus != "human_verified" && authority.verificationStatus != "verified" && !isRepealed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onVerify,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.good),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("توثيق المصدر", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
