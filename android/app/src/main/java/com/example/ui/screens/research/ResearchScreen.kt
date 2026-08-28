package com.example.ui.screens.research

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthorityMatchDto
import com.example.data.model.ResearchResponse
import com.example.ui.components.ConfirmedChip
import com.example.ui.components.DangerChip
import com.example.ui.components.ProvisionalChip
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors

@Composable
fun ResearchScreen(
    onSearch: (String) -> Unit,
    isSearching: Boolean,
    researchResult: ResearchResponse?,
    onNavigateToCorpus: () -> Unit
) {
    val colors = LocalHoyaamColors.current
    var query by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "بحث قانوني موثّق",
                    color = colors.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )
                Text(
                    text = "البحث في أحكام النقض والقوانين المصرية الموثّقة مع حظر التوليد غير المستند لمصدر",
                    color = colors.textDim,
                    fontSize = 13.sp,
                    fontFamily = ArabicSansFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Search Input Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("مثال: سقوط الحق في الاستئناف، بطلان إعلان صحيفة الدعوى…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textDim) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("research_query_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.card,
                        focusedBorderColor = colors.text,
                        unfocusedBorderColor = colors.border
                    )
                )

                Button(
                    onClick = { onSearch(query) },
                    enabled = query.isNotBlank() && !isSearching,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.text),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("research_submit_button")
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("بحث", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Results Section
        if (researchResult != null) {
            val matches = researchResult.matches

            if (!researchResult.answer.isNullOrBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "الرأي القانوني المستخلص:",
                                color = colors.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArabicSansFontFamily
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = researchResult.answer,
                                color = colors.text,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                }
            }

            if (matches.isEmpty() && researchResult.answer.isNullOrBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "لا توجد مصادر موثّقة مطابقة لسؤالك في قاعدة البيانات حتى الآن.",
                                color = colors.warn,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArabicSansFontFamily
                            )
                            Text(
                                text = "النظام يمتنع عن توليد نصوص وهمية. يمكنك إضافة السند والمصدر القانوني يدوياً لتوثيقه واستخدامه في القضايا.",
                                color = colors.textDim,
                                fontSize = 12.sp,
                                fontFamily = ArabicSansFontFamily,
                                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                            )
                            Button(
                                onClick = onNavigateToCorpus,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                            ) {
                                Text("الانتقال إلى المصادر القانونية", fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else if (matches.isNotEmpty()) {
                item {
                    Text(
                        text = "نتائج البحث (${matches.size} سند قانوني):",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ArabicSansFontFamily
                    )
                }

                items(matches) { match ->
                    ResearchMatchCard(match = match)
                }
            }
        }
    }
}

@Composable
fun ResearchMatchCard(match: AuthorityMatchDto) {
    val colors = LocalHoyaamColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.title,
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ArabicSansFontFamily,
                    modifier = Modifier.weight(1f)
                )

                when (match.verificationStatus) {
                    "human_verified", "verified" -> ConfirmedChip(text = "موثّق بشرياً")
                    "disputed" -> DangerChip(text = "متنازع عليه")
                    else -> ProvisionalChip(text = "مسجّل")
                }
            }

            Text(
                text = "السند: ${match.citation}",
                color = colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (!match.chunkRef.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.inset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = match.chunkRef,
                        color = colors.text,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(10.dp),
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        }
    }
}
