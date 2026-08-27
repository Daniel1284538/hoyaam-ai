package com.example.ui.screens.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExtractionDto
import com.example.ui.components.ConfidenceChip
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors

@Composable
fun ReviewQueueScreen(
    extractions: List<ExtractionDto>,
    isLoading: Boolean,
    onReviewExtraction: (extractionId: String, action: String, correctedValue: String?) -> Unit
) {
    val colors = LocalHoyaamColors.current

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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "مراجعة الاستخراج الذكي",
                    color = colors.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )
                Text(
                    text = "كل حقل هنا كانت ثقة النموذج فيه أقل من 85% — لم يُطبّق تلقائياً على القضية لحين المراجعة البشرية",
                    color = colors.textDim,
                    fontSize = 13.sp,
                    fontFamily = ArabicSansFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (extractions.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "لا توجد مستخرجات بانتظار المراجعة.",
                            color = colors.good,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ArabicSansFontFamily
                        )
                        Text(
                            text = "تمت مراجعة وتأكيد كافة الحقول المستخرجة من المستندات.",
                            color = colors.textDim,
                            fontSize = 12.sp,
                            fontFamily = ArabicSansFontFamily,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(extractions, key = { it.id }) { extraction ->
                ExtractionReviewCard(
                    extraction = extraction,
                    onAction = { action, correctedValue ->
                        onReviewExtraction(extraction.id, action, correctedValue)
                    }
                )
            }
        }
    }
}

@Composable
fun ExtractionReviewCard(
    extraction: ExtractionDto,
    onAction: (action: String, correctedValue: String?) -> Unit
) {
    val colors = LocalHoyaamColors.current
    var currentValue by remember(extraction.id) { mutableStateOf(extraction.fieldValue.orEmpty()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = extraction.fieldKey,
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ArabicSansFontFamily
                    )
                    Text(
                        text = extraction.matters?.matterLabel ?: extraction.matterId,
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = ArabicSansFontFamily
                    )
                }

                ConfidenceChip(confidence = extraction.confidence ?: 0.0)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = currentValue,
                onValueChange = { currentValue = it },
                label = { Text("القيمة المستخرجة") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.text,
                    unfocusedBorderColor = colors.border
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject Button
                Button(
                    onClick = { onAction("reject", null) },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger.copy(alpha = 0.12f),
                        contentColor = colors.danger
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("رفض", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Correct / Accept Button
                if (currentValue != extraction.fieldValue.orEmpty()) {
                    Button(
                        onClick = { onAction("correct", currentValue) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصحيح وحفظ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { onAction("confirm", null) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.good),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("قبول وتطبيق", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
