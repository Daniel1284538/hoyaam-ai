package com.example.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.LocalCaseNoteEntity
import com.example.data.local.entities.LocalCaseSummaryEntity
import com.example.data.local.entities.LocalScannedDocumentEntity
import com.example.data.model.ArchiveSearchResponse
import com.example.data.model.MatterDto
import com.example.ui.components.BidiMonoText
import com.example.ui.components.StageChip
import com.example.ui.screens.matters.STAGE_LABELS_MAP
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors
import java.text.SimpleDateFormat
import java.util.*

enum class SearchFilterScope(val label: String) {
    ALL("الكل (شامل)"),
    MATTERS("القضايا والملخصات"),
    NOTES("ملاحظات المحامي"),
    SCANS("المستندات الممسوحة")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveSearchScreen(
    allMatters: List<MatterDto>,
    localSearchQuery: String = "",
    searchSummaries: List<LocalCaseSummaryEntity> = emptyList(),
    searchNotes: List<LocalCaseNoteEntity> = emptyList(),
    searchScans: List<LocalScannedDocumentEntity> = emptyList(),
    onSearchQueryChange: (String) -> Unit = {},
    onNavigateToMatter: (String) -> Unit,
    // Real litigation-search-archive results — the firm's own past
    // filings, server-side, across every matter the caller can access.
    // Separate from the sections below, which only ever search this
    // one device's local Room cache and were already clearly labeled as
    // such — this section is the part that was previously missing
    // entirely, with no way to reach the real archive from this screen.
    archiveSearchResult: ArchiveSearchResponse? = null,
    isSearchingArchive: Boolean = false,
    onSearchArchiveRemote: (String) -> Unit = {}
) {
    val colors = LocalHoyaamColors.current
    var currentScope by remember { mutableStateOf(SearchFilterScope.ALL) }

    val quickSearches = listOf("محكمة النقض", "استئناف القاهرة", "جنوب القاهرة", "فسخ عقد", "تعويض", "عمالي", "مرافعة")

    // Filtered Matters (Remote + Local)
    val matchedMatters = remember(allMatters, localSearchQuery) {
        if (localSearchQuery.isBlank()) {
            emptyList()
        } else {
            allMatters.filter { m ->
                m.matterLabel.contains(localSearchQuery, ignoreCase = true) ||
                        (m.subject?.contains(localSearchQuery, ignoreCase = true) == true) ||
                        (m.caseNumber?.contains(localSearchQuery, ignoreCase = true) == true) ||
                        (m.court?.contains(localSearchQuery, ignoreCase = true) == true)
            }
        }
    }

    val totalResultsCount = remember(localSearchQuery, matchedMatters, searchSummaries, searchNotes, searchScans, currentScope) {
        if (localSearchQuery.isBlank()) 0
        else when (currentScope) {
            SearchFilterScope.ALL -> matchedMatters.size + searchSummaries.size + searchNotes.size + searchScans.size
            SearchFilterScope.MATTERS -> matchedMatters.size + searchSummaries.size
            SearchFilterScope.NOTES -> searchNotes.size
            SearchFilterScope.SCANS -> searchScans.size
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "البحث في الأرشيف وقاعدة البيانات المحلية",
                        color = colors.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                }
                Text(
                    text = "بحث فوري في نصوص القضايا، ملخصات الذكاء الاصطناعي، مذكرات المحامي والمستندات الممسوحة (Room Database)",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    fontFamily = ArabicSansFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Search Input
        item {
            OutlinedTextField(
                value = localSearchQuery,
                onValueChange = { onSearchQueryChange(it) },
                placeholder = { Text("ابحث في كامل الأرشيف والبيانات المحلية (Room)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textDim) },
                trailingIcon = {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث", tint = colors.textDim)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("archive_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border
                )
            )
        }

        // Real firm-wide archive search (server-side) — a deliberate,
        // explicit action rather than firing on every keystroke, since
        // it's a real network call across the firm's own past filings.
        if (localSearchQuery.isNotBlank()) {
            item {
                Button(
                    onClick = { onSearchArchiveRemote(localSearchQuery) },
                    enabled = !isSearchingArchive,
                    modifier = Modifier.fillMaxWidth().testTag("search_archive_remote_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    if (isSearchingArchive) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جارٍ البحث في أرشيف المكتب…", fontFamily = ArabicSansFontFamily)
                    } else {
                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ابحث في أرشيف المكتب الكامل (الخادم)", fontFamily = ArabicSansFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }

            archiveSearchResult?.let { result ->
                item {
                    Text(
                        text = "نتائج أرشيف المكتب (${result.results.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        fontFamily = ArabicSansFontFamily
                    )
                }
                if (!result.note.isNullOrBlank()) {
                    item {
                        Text(result.note, color = colors.textDim, fontSize = 11.sp, fontFamily = ArabicSansFontFamily)
                    }
                }
                items(result.results, key = { "archive_${it.chunkId}" }) { hit ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { hit.matterId?.let { onNavigateToMatter(it) } },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.good.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = colors.good, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = hit.matterLabel ?: hit.documentName ?: "نتيجة",
                                    color = colors.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                            if (!hit.excerpt.isNullOrBlank()) {
                                Text(
                                    text = hit.excerpt,
                                    color = colors.textDim,
                                    fontSize = 12.sp,
                                    maxLines = 3,
                                    modifier = Modifier.padding(top = 6.dp),
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                            Text(
                                text = listOfNotNull(hit.court, hit.caseNumber?.let { "رقم $it/${hit.caseYear ?: ""}" }, hit.pageNumber?.let { "ص. $it" }).joinToString(" • "),
                                color = colors.textDim,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                if (result.results.isEmpty()) {
                    item {
                        Text(
                            text = "لا نتائج من أرشيف المكتب لهذا البحث.",
                            color = colors.textDim,
                            fontSize = 12.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }
            }
        }

        // Scope Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchFilterScope.values()) { scope ->
                    val isSelected = currentScope == scope
                    FilterChip(
                        selected = isSelected,
                        onClick = { currentScope = scope },
                        label = {
                            Text(
                                text = scope.label,
                                fontSize = 12.sp,
                                fontFamily = ArabicSansFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent,
                            selectedLabelColor = Color.White,
                            containerColor = colors.card,
                            labelColor = colors.text2
                        ),
                        border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.border)
                    )
                }
            }
        }

        // Quick Search Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "اقتراحات سريعة:",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    fontFamily = ArabicSansFontFamily
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickSearches) { tag ->
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (localSearchQuery == tag) colors.heroBg else colors.inset,
                            border = BorderStroke(1.dp, if (localSearchQuery == tag) colors.accent else colors.border),
                            modifier = Modifier.clickable {
                                onSearchQueryChange(if (localSearchQuery == tag) "" else tag)
                            }
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                color = if (localSearchQuery == tag) colors.heroText else colors.text2,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                }
            }
        }

        // Search Results Section
        if (localSearchQuery.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نتائج البحث في قاعدة البيانات ($totalResultsCount نتيجة):",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }

            if (totalResultsCount == 0) {
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
                            Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "لم يتم العثور على أية نتائج مطابقة للبحث في قاعدة البيانات.",
                                color = colors.textDim,
                                fontSize = 13.sp,
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                }
            }

            // 1. Matched Matters & Case Summaries
            if (currentScope == SearchFilterScope.ALL || currentScope == SearchFilterScope.MATTERS) {
                if (matchedMatters.isNotEmpty()) {
                    item {
                        Text(
                            text = "القضايا وملفاتها (${matchedMatters.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            fontFamily = ArabicSansFontFamily
                        )
                    }

                    items(matchedMatters, key = { "matter_${it.id}" }) { matter ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToMatter(matter.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = colors.card,
                            border = BorderStroke(1.dp, colors.border)
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
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ArabicSansFontFamily
                                    )
                                    matter.stage?.let { stageKey ->
                                        StageChip(text = STAGE_LABELS_MAP[stageKey] ?: stageKey)
                                    }
                                }

                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = matter.court ?: "المحكمة غير محددة",
                                        color = colors.text2,
                                        fontSize = 12.sp
                                    )
                                    if (!matter.caseNumber.isNullOrEmpty()) {
                                        Text("•", color = colors.border)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("رقم: ", color = colors.textDim, fontSize = 11.sp)
                                            BidiMonoText(text = "${matter.caseNumber}/${matter.caseYear ?: ""}", color = colors.text, fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (!matter.subject.isNullOrEmpty()) {
                                    Text(
                                        text = matter.subject,
                                        color = colors.textDim,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Matched Local Summaries from Room
                if (searchSummaries.isNotEmpty()) {
                    item {
                        Text(
                            text = "ملخصات القضايا المحفوظة في Room (${searchSummaries.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            fontFamily = ArabicSansFontFamily
                        )
                    }

                    items(searchSummaries, key = { "summary_${it.matterId}" }) { summary ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToMatter(summary.matterId) },
                            shape = RoundedCornerShape(12.dp),
                            color = colors.card,
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Storage, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = summary.matterLabel,
                                            color = colors.text,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = ArabicSansFontFamily
                                        )
                                    }
                                    Text(
                                        text = "Room Local",
                                        color = colors.accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (!summary.summaryText.isNullOrBlank()) {
                                    Text(
                                        text = summary.summaryText,
                                        color = colors.textDim,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.padding(top = 6.dp),
                                        fontFamily = ArabicSansFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Matched Lawyer Notes from Room
            if (currentScope == SearchFilterScope.ALL || currentScope == SearchFilterScope.NOTES) {
                if (searchNotes.isNotEmpty()) {
                    item {
                        Text(
                            text = "ملاحظات ومذكرات المحامي في Room (${searchNotes.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            fontFamily = ArabicSansFontFamily
                        )
                    }

                    items(searchNotes, key = { "note_${it.id}" }) { note ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToMatter(note.matterId) },
                            shape = RoundedCornerShape(12.dp),
                            color = colors.card,
                            border = BorderStroke(1.dp, if (note.isPinned) colors.accent else colors.border)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (note.isPinned) {
                                            Icon(Icons.Default.PushPin, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = note.title,
                                            color = colors.text,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = ArabicSansFontFamily
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.heroBg
                                    ) {
                                        Text(
                                            text = note.tag,
                                            color = colors.heroText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = note.content,
                                    color = colors.text2,
                                    fontSize = 12.sp,
                                    maxLines = 3,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                    fontFamily = ArabicSansFontFamily
                                )

                                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
                                Text(
                                    text = "تاريخ التحديث: ${sdf.format(Date(note.updatedAt))}",
                                    color = colors.textDim,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Matched Scanned Documents from Room
            if (currentScope == SearchFilterScope.ALL || currentScope == SearchFilterScope.SCANS) {
                if (searchScans.isNotEmpty()) {
                    item {
                        Text(
                            text = "المستندات الممسوحة ضوئياً في Room (${searchScans.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            fontFamily = ArabicSansFontFamily
                        )
                    }

                    items(searchScans, key = { "scan_${it.id}" }) { scan ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { scan.matterId?.let { onNavigateToMatter(it) } },
                            shape = RoundedCornerShape(12.dp),
                            color = colors.card,
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = scan.title,
                                        color = colors.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ArabicSansFontFamily
                                    )
                                    Text(
                                        text = "النوع: ${scan.docType} • مستند ممسوح",
                                        color = colors.textDim,
                                        fontSize = 11.sp,
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
