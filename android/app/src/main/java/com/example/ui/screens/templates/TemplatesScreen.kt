package com.example.ui.screens.templates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TemplateDto
import com.example.ui.components.StageChip
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors

@Composable
fun TemplatesScreen(
    templates: List<TemplateDto>,
    isLoading: Boolean,
    onOpenAddTemplateDialog: () -> Unit
) {
    val colors = LocalHoyaamColors.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddTemplateDialog,
                containerColor = colors.text,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_template_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("قالب جديد", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                        text = "قوالب الصياغة المعتمدة",
                        color = colors.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                    Text(
                        text = "نماذج قياسية بصيغ معتمدة للمكتب لملئها آلياً أو استخدامها في صياغة المذكرات",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        fontFamily = ArabicSansFontFamily,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.text)
                    }
                }
            } else if (templates.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Text(
                            text = "لا توجد قوالب مسجّلة بعد. اضغط «قالب جديد» لإضافة نموذج للمكتب.",
                            color = colors.textDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(20.dp),
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }
            } else {
                items(templates, key = { it.id }) { template ->
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
                                    text = template.title,
                                    color = colors.text,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ArabicSansFontFamily,
                                    modifier = Modifier.weight(1f)
                                )
                                StageChip(text = template.docType)
                            }

                            if (!template.contentText.isNullOrEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.inset,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                ) {
                                    Text(
                                        text = template.contentText,
                                        color = colors.textDim,
                                        fontSize = 12.sp,
                                        maxLines = 3,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.padding(10.dp),
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
}
