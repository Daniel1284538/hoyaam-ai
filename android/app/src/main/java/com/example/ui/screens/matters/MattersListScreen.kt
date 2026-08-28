package com.example.ui.screens.matters

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.LocalCaseSummaryEntity
import com.example.data.model.MatterDto
import com.example.ui.components.BidiMonoText
import com.example.ui.components.ConfirmedChip
import com.example.ui.components.StageChip
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors

val STAGE_LABELS_MAP = mapOf(
    "first_instance" to "ابتدائي",
    "appeal" to "استئناف",
    "cassation" to "نقض",
    "execution" to "تنفيذ"
)

val PRESET_CASE_TAGS = listOf(
    "Active" to "سارية / نشطة",
    "Archived" to "مؤرشفة",
    "Court Filing" to "إيداع محكمة",
    "Client Correspondence" to "مراسلات موكلين",
    "Urgent" to "عاجل",
    "Commercial" to "تجاري",
    "Civil" to "مدني",
    "Labor" to "عمالي",
    "Arbitration" to "تحكيم"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MattersListScreen(
    matters: List<MatterDto>,
    localSummaries: List<LocalCaseSummaryEntity> = emptyList(),
    isLoading: Boolean,
    onMatterClick: (String) -> Unit,
    onOpenNewMatterDialog: () -> Unit,
    onUpdateMatterTags: ((String, String) -> Unit)? = null
) {
    val colors = LocalHoyaamColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var selectedStageFilter by remember { mutableStateOf<String?>(null) }

    var matterForTagEdit by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(matterId, currentTags)

    // Map matterId to tags from localSummaries
    val tagsMap = remember(localSummaries) {
        localSummaries.associate { it.matterId to it.tags }
    }

    val filteredMatters = remember(matters, searchQuery, selectedTagFilter, selectedStageFilter, tagsMap) {
        matters.filter { m ->
            val matterTags = tagsMap[m.id] ?: (if (m.status == "archived") "Archived" else "Active")

            val matchQuery = searchQuery.isBlank() ||
                    m.matterLabel.contains(searchQuery, ignoreCase = true) ||
                    (m.court?.contains(searchQuery, ignoreCase = true) == true) ||
                    (m.caseNumber?.contains(searchQuery, ignoreCase = true) == true) ||
                    (m.subject?.contains(searchQuery, ignoreCase = true) == true) ||
                    matterTags.contains(searchQuery, ignoreCase = true)

            val matchStage = selectedStageFilter == null || m.stage == selectedStageFilter

            val matchTag = when (selectedTagFilter) {
                null -> true
                "Active" -> matterTags.contains("Active", ignoreCase = true) || m.status == "active"
                "Archived" -> matterTags.contains("Archived", ignoreCase = true) || m.status == "archived"
                else -> matterTags.contains(selectedTagFilter ?: "", ignoreCase = true)
            }

            matchQuery && matchStage && matchTag
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenNewMatterDialog,
                containerColor = colors.text,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_matter_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "قضية جديدة")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("قضية جديدة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        },
        containerColor = colors.bg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "القضايا وملفات الدعاوى",
                        color = colors.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                    Text(
                        text = "قائمة ملفات القضايا وتصنيفاتها الإجرائية والموضوعية",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث باسم القضية، الوسم، المحكمة، رقم الدعوى…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textDim) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = colors.textDim)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("matters_search_input"),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedBorderColor = colors.text,
                    unfocusedBorderColor = colors.border
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category & Tag Filter Chips (Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTagFilter == null,
                    onClick = { selectedTagFilter = null },
                    label = { Text("جميع الوسوم") },
                    leadingIcon = { Icon(Icons.Outlined.Label, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.text,
                        selectedLabelColor = Color.White
                    )
                )

                PRESET_CASE_TAGS.forEach { (tagKey, tagLabel) ->
                    val isSelected = selectedTagFilter == tagKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTagFilter = if (isSelected) null else tagKey },
                        label = { Text(tagLabel) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (tagKey) {
                                "Active" -> colors.good
                                "Archived" -> Color(0xFF6B7280)
                                "Court Filing" -> colors.accent
                                "Client Correspondence" -> Color(0xFF8B5CF6)
                                "Urgent" -> colors.danger
                                else -> colors.text
                            },
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Stage Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedStageFilter == null,
                    onClick = { selectedStageFilter = null },
                    label = { Text("كل المراحل") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent,
                        selectedLabelColor = Color.White
                    )
                )
                STAGE_LABELS_MAP.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedStageFilter == key,
                        onClick = { selectedStageFilter = if (selectedStageFilter == key) null else key },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.text)
                }
            } else if (filteredMatters.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedTagFilter != null) "لا توجد نتائج مطابقة للتصنيف أو البحث المحدد." else "لا توجد قضايا مرئية لك بعد.",
                            color = colors.textDim,
                            fontSize = 14.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMatters, key = { it.id }) { matter ->
                        val matterTags = tagsMap[matter.id] ?: (if (matter.status == "archived") "Archived" else "Active")
                        MatterCardWithTags(
                            matter = matter,
                            tags = matterTags,
                            onClick = { onMatterClick(matter.id) },
                            onEditTagsClick = {
                                matterForTagEdit = matter.id to matterTags
                            }
                        )
                    }
                }
            }
        }
    }

    // Tag Editor Dialog
    matterForTagEdit?.let { (matterId, currentTags) ->
        CaseTagEditorDialog(
            currentTags = currentTags,
            onDismiss = { matterForTagEdit = null },
            onSaveTags = { newTags ->
                onUpdateMatterTags?.invoke(matterId, newTags)
                matterForTagEdit = null
            }
        )
    }
}

@Composable
fun MatterCardWithTags(
    matter: MatterDto,
    tags: String,
    onClick: () -> Unit,
    onEditTagsClick: () -> Unit
) {
    val colors = LocalHoyaamColors.current
    val tagList = remember(tags) {
        tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("matter_card_${matter.id}"),
        shape = RoundedCornerShape(12.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = matter.matterLabel,
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ArabicSansFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onEditTagsClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Label,
                            contentDescription = "تعديل الوسوم",
                            tint = colors.textDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    ConfirmedChip(text = if (matter.status == "active") "سارية" else matter.status)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = listOfNotNull(matter.court, matter.circuit?.let { "($it)" }).joinToString(" ").ifEmpty { "المحكمة غير محددة" },
                        color = colors.text2,
                        fontSize = 12.sp,
                        fontFamily = ArabicSansFontFamily
                    )

                    if (!matter.caseNumber.isNullOrEmpty()) {
                        Text("•", color = colors.border)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("رقم: ", color = colors.textDim, fontSize = 11.sp)
                            BidiMonoText(
                                text = "${matter.caseNumber}/${matter.caseYear ?: ""}",
                                color = colors.text,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                matter.stage?.let { stageKey ->
                    StageChip(text = STAGE_LABELS_MAP[stageKey] ?: stageKey)
                }
            }

            // Tags Display Row
            if (tagList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tagList.take(4).forEach { tag ->
                        val (chipBg, chipText) = getTagColors(tag, colors)
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = chipBg,
                            border = BorderStroke(0.5.dp, chipText.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = getTagDisplayLabel(tag),
                                color = chipText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                    if (tagList.size > 4) {
                        Text(
                            text = "+${tagList.size - 4}",
                            color = colors.textDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!matter.subject.isNullOrEmpty()) {
                Text(
                    text = matter.subject,
                    color = colors.textDim,
                    fontSize = 12.sp,
                    fontFamily = ArabicSansFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun CaseTagEditorDialog(
    currentTags: String,
    onDismiss: () -> Unit,
    onSaveTags: (String) -> Unit
) {
    val colors = LocalHoyaamColors.current
    var selectedTags by remember {
        mutableStateOf(currentTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }
    var customTagInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تصنيف ووسوم القضية",
                fontFamily = AmiriFontFamily,
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "اختر من التصنيفات الجاهزة أو أضف وسماً مخصصاً لتسهيل الفرز والأرشفة:",
                    fontSize = 12.sp,
                    color = colors.textDim,
                    fontFamily = ArabicSansFontFamily
                )

                // Quick preset tags
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESET_CASE_TAGS.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pair.forEach { (tagKey, tagLabel) ->
                                val isChecked = selectedTags.contains(tagKey)
                                FilterChip(
                                    selected = isChecked,
                                    onClick = {
                                        selectedTags = if (isChecked) {
                                            selectedTags - tagKey
                                        } else {
                                            selectedTags + tagKey
                                        }
                                    },
                                    label = { Text(tagLabel, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Custom Tag Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customTagInput,
                        onValueChange = { customTagInput = it },
                        placeholder = { Text("وسم مخصص…", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            if (customTagInput.isNotBlank()) {
                                selectedTags = selectedTags + customTagInput.trim()
                                customTagInput = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        enabled = customTagInput.isNotBlank()
                    ) {
                        Text("إضافة")
                    }
                }

                // Active Tags Badges
                if (selectedTags.isNotEmpty()) {
                    Text("الوسوم الحالية:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = colors.heroBg,
                                modifier = Modifier.clickable {
                                    selectedTags = selectedTags - tag
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(getTagDisplayLabel(tag), fontSize = 11.sp, color = colors.heroText)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Close, contentDescription = "إزالة", tint = colors.heroText, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveTags(selectedTags.joinToString(", "))
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("حفظ التصنيفات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

fun getTagDisplayLabel(tag: String): String {
    return when (tag) {
        "Active" -> "سارية"
        "Archived" -> "مؤرشفة"
        "Court Filing" -> "إيداع محكمة"
        "Client Correspondence" -> "مراسلات موكلين"
        "Urgent" -> "عاجل"
        "Commercial" -> "تجاري"
        "Civil" -> "مدني"
        "Labor" -> "عمالي"
        "Arbitration" -> "تحكيم"
        else -> tag
    }
}

@Composable
fun getTagColors(tag: String, colors: com.example.ui.theme.CustomHoyaamColors): Pair<Color, Color> {
    return when (tag) {
        "Active" -> colors.good.copy(alpha = 0.12f) to colors.good
        "Archived" -> Color(0xFF6B7280).copy(alpha = 0.12f) to Color(0xFF6B7280)
        "Court Filing" -> colors.accent.copy(alpha = 0.12f) to colors.accent
        "Client Correspondence" -> Color(0xFF8B5CF6).copy(alpha = 0.12f) to Color(0xFF8B5CF6)
        "Urgent" -> colors.danger.copy(alpha = 0.12f) to colors.danger
        else -> colors.inset to colors.text2
    }
}
