package com.example.ui.screens.matters

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.LocalCaseNoteEntity
import com.example.data.local.entities.LocalCaseSummaryEntity
import com.example.data.local.entities.LocalScannedDocumentEntity
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors
import com.example.util.PdfExporter
import java.text.SimpleDateFormat
import java.util.*

enum class MatterTab(val title: String) {
    OVERVIEW("نظرة عامة"),
    TIMELINE("مخطط المسار"),
    NOTES("الملاحظات والملخص"),
    PARTIES("الأطراف"),
    HEARINGS("الجلسات"),
    DEADLINES("المواعيد"),
    DOCUMENTS("المستندات"),
    DRAFTS("المسودات"),
    CHRONOLOGY("التسلسل الزمني")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatterDetailScreen(
    matter: MatterDto,
    parties: List<PartyDto>,
    hearings: List<HearingDto>,
    deadlines: List<DeadlineDto>,
    documents: List<DocumentDto>,
    drafts: List<DraftDto>,
    cachedAnalysis: CachedCaseAnalysis?,
    isAnalyzing: Boolean,
    isGeneratingChronology: Boolean,
    chronologyResponse: ChronologyResponse?,
    localSummary: LocalCaseSummaryEntity? = null,
    localNotes: List<LocalCaseNoteEntity> = emptyList(),
    localScans: List<LocalScannedDocumentEntity> = emptyList(),
    onBackClick: () -> Unit,
    onEditMatterClick: () -> Unit,
    onUploadDocsClick: () -> Unit,
    onAnalyzeCaseClick: () -> Unit,
    onAddPartyClick: () -> Unit,
    onEditPartyClick: (PartyDto) -> Unit,
    onDeletePartyClick: (String) -> Unit,
    onRecordHearingClick: () -> Unit,
    onProposeDeadlineClick: () -> Unit,
    onConfirmDeadlineClick: (String) -> Unit,
    onOpenDocument: (DocumentDto) -> Unit,
    onNewDraftClick: () -> Unit,
    onNewMemoClick: () -> Unit,
    onFillTemplateClick: () -> Unit,
    onInspectCitationsClick: (DraftDto) -> Unit,
    onExportDraftClick: (String) -> Unit,
    onGenerateChronologyClick: () -> Unit,
    onAddLocalNote: ((String, String, String, Boolean) -> Unit)? = null,
    onTogglePinNote: ((Long, Boolean) -> Unit)? = null,
    onDeleteLocalNote: ((Long) -> Unit)? = null,
    onScanDocumentClick: (() -> Unit)? = null,
    onDeleteLocalScan: ((String) -> Unit)? = null,
    onUpdateMatterTags: ((String, String) -> Unit)? = null,
    onUpdateScanTags: ((String, String) -> Unit)? = null,
    onSyncDeadlineToCalendar: ((DeadlineDto) -> Unit)? = null,
    onSyncHearingToCalendar: ((HearingDto) -> Unit)? = null,
    // litigation-summarize / litigation-hearing-briefing — previously
    // never wired into this app at all, added on request.
    isSummarizing: Boolean = false,
    summaryResult: SummarizeResponse? = null,
    onSummarizeCaseClick: () -> Unit = {},
    onSummarizeDocumentClick: (String) -> Unit = {},
    onClearSummaryResult: () -> Unit = {},
    isGeneratingBriefing: Boolean = false,
    onGenerateBriefingClick: () -> Unit = {},
    // litigation-check-conflicts — previously never wired into this app
    // at all.
    isCheckingConflicts: Boolean = false,
    conflictCheckResult: ConflictCheckResponse? = null,
    onCheckConflicts: (List<String>) -> Unit = {},
    onClearConflictCheckResult: () -> Unit = {}
) {
    val colors = LocalHoyaamColors.current
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(MatterTab.OVERVIEW) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showConflictCheckDialog by remember { mutableStateOf(false) }

    if (summaryResult != null) {
        SummaryResultDialog(result = summaryResult, onDismiss = onClearSummaryResult)
    }

    if (showConflictCheckDialog) {
        ConflictCheckDialog(
            initialNames = parties.map { it.name }.filter { it.isNotBlank() },
            isLoading = isCheckingConflicts,
            result = conflictCheckResult,
            onCheck = onCheckConflicts,
            onDismiss = {
                showConflictCheckDialog = false
                onClearConflictCheckResult()
            }
        )
    }

    val currentMatterTags = localSummary?.tags ?: if (matter.status == "archived") "Archived" else "Active, Court Filing"
    val tagsList = remember(currentMatterTags) {
        currentMatterTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Matter Header Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.card,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.inset, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع", tint = colors.text)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showTagDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .background(colors.inset, RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Outlined.Label, contentDescription = "تعديل الوسوم", tint = colors.text, modifier = Modifier.size(16.dp))
                        }

                        if (onScanDocumentClick != null) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = colors.heroBg,
                                modifier = Modifier.clickable { onScanDocumentClick() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = colors.heroText, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مسح ضوئي", color = colors.heroText, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                                }
                            }
                        }
                        ConfirmedChip(text = if (matter.status == "active") "سارية" else matter.status)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = matter.matterLabel,
                    color = colors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المحكمة: ${matter.court ?: "—"}",
                        color = colors.text2,
                        fontSize = 13.sp,
                        fontFamily = ArabicSansFontFamily
                    )
                    if (!matter.caseNumber.isNullOrEmpty()) {
                        Text("•", color = colors.border)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("رقم القضية: ", color = colors.textDim, fontSize = 12.sp)
                            BidiMonoText(text = "${matter.caseNumber}/${matter.caseYear ?: ""}", color = colors.text, fontSize = 13.sp)
                        }
                    }
                    matter.stage?.let { stageKey ->
                        Text("•", color = colors.border)
                        StageChip(text = STAGE_LABELS_MAP[stageKey] ?: stageKey)
                    }
                }

                // Tags Chips in Header
                if (tagsList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tagsList.forEach { tag ->
                            val (chipBg, chipText) = getTagColors(tag, colors)
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = chipBg,
                                border = BorderStroke(0.5.dp, chipText.copy(alpha = 0.3f)),
                                modifier = Modifier.clickable { showTagDialog = true }
                            ) {
                                Text(
                                    text = getTagDisplayLabel(tag),
                                    color = chipText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scrollable Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = colors.card,
            contentColor = colors.accent,
            edgePadding = 12.dp,
            divider = { HorizontalDivider(color = colors.border) }
        ) {
            MatterTab.values().forEach { tab ->
                val countBadge = when (tab) {
                    MatterTab.NOTES -> if (localNotes.isNotEmpty()) " (${localNotes.size})" else ""
                    MatterTab.PARTIES -> if (parties.isNotEmpty()) " (${parties.size})" else ""
                    MatterTab.HEARINGS -> if (hearings.isNotEmpty()) " (${hearings.size})" else ""
                    MatterTab.DEADLINES -> if (deadlines.isNotEmpty()) " (${deadlines.size})" else ""
                    MatterTab.DOCUMENTS -> if (documents.isNotEmpty() || localScans.isNotEmpty()) " (${documents.size + localScans.size})" else ""
                    MatterTab.DRAFTS -> if (drafts.isNotEmpty()) " (${drafts.size})" else ""
                    else -> ""
                }
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = "${tab.title}$countBadge",
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            fontFamily = ArabicSansFontFamily,
                            color = if (selectedTab == tab) colors.accent else colors.text2
                        )
                    }
                )
            }
        }

        // Tab Contents
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedTab) {
                MatterTab.OVERVIEW -> {
                    OverviewTabContent(
                        matter = matter,
                        deadlines = deadlines,
                        hearings = hearings,
                        cachedAnalysis = cachedAnalysis,
                        isAnalyzing = isAnalyzing,
                        onEditMatterClick = onEditMatterClick,
                        onUploadDocsClick = onUploadDocsClick,
                        onScanDocumentClick = onScanDocumentClick,
                        onAnalyzeCaseClick = onAnalyzeCaseClick,
                        onConfirmDeadline = onConfirmDeadlineClick,
                        onProposeDeadlineClick = onProposeDeadlineClick,
                        onOpenWebSource = { uri ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        },
                        isSummarizing = isSummarizing,
                        onSummarizeCaseClick = onSummarizeCaseClick
                    )
                }

                MatterTab.TIMELINE -> {
                    LitigationTimelineChart(
                        matter = matter,
                        deadlines = deadlines,
                        hearings = hearings,
                        onConfirmDeadline = onConfirmDeadlineClick,
                        onProposeDeadlineClick = onProposeDeadlineClick
                    )
                }

                MatterTab.NOTES -> {
                    NotesTabContent(
                        matter = matter,
                        localSummary = localSummary,
                        notes = localNotes,
                        scans = localScans,
                        hearings = hearings,
                        deadlines = deadlines,
                        onAddNote = { title, content, tag, isPinned ->
                            onAddLocalNote?.invoke(title, content, tag, isPinned)
                        },
                        onTogglePinNote = { id, currentPin ->
                            onTogglePinNote?.invoke(id, currentPin)
                        },
                        onDeleteNote = { id ->
                            onDeleteLocalNote?.invoke(id)
                        },
                        onScanDocumentClick = onScanDocumentClick,
                        onDeleteScan = { scanId ->
                            onDeleteLocalScan?.invoke(scanId)
                        }
                    )
                }

                MatterTab.PARTIES -> {
                    PartiesTabContent(
                        parties = parties,
                        onAddPartyClick = onAddPartyClick,
                        onEditPartyClick = onEditPartyClick,
                        onDeletePartyClick = onDeletePartyClick,
                        onCheckConflictsClick = { showConflictCheckDialog = true }
                    )
                }

                MatterTab.HEARINGS -> {
                    HearingsTabContent(
                        hearings = hearings,
                        matterLabel = matter.matterLabel,
                        court = matter.court,
                        onRecordHearingClick = onRecordHearingClick,
                        onSyncHearingToCalendar = onSyncHearingToCalendar
                    )
                }

                MatterTab.DEADLINES -> {
                    DeadlinesTabContent(
                        deadlines = deadlines,
                        matter = matter,
                        hearings = hearings,
                        onProposeDeadlineClick = onProposeDeadlineClick,
                        onConfirmDeadlineClick = onConfirmDeadlineClick,
                        onSyncDeadlineToCalendar = onSyncDeadlineToCalendar
                    )
                }

                MatterTab.DOCUMENTS -> {
                    DocumentsTabContent(
                        documents = documents,
                        localScans = localScans,
                        onUploadDocsClick = onUploadDocsClick,
                        onScanDocumentClick = onScanDocumentClick,
                        onOpenDocument = onOpenDocument,
                        onDeleteLocalScan = onDeleteLocalScan,
                        onUpdateScanTags = onUpdateScanTags,
                        isSummarizing = isSummarizing,
                        onSummarizeDocumentClick = onSummarizeDocumentClick
                    )
                }

                MatterTab.DRAFTS -> {
                    DraftsTabContent(
                        drafts = drafts,
                        onNewDraftClick = onNewDraftClick,
                        onNewMemoClick = onNewMemoClick,
                        onFillTemplateClick = onFillTemplateClick,
                        onInspectCitationsClick = onInspectCitationsClick,
                        onExportDraftClick = onExportDraftClick,
                        isGeneratingBriefing = isGeneratingBriefing,
                        onGenerateBriefingClick = onGenerateBriefingClick
                    )
                }

                MatterTab.CHRONOLOGY -> {
                    ChronologyTabContent(
                        chronology = chronologyResponse,
                        isGenerating = isGeneratingChronology,
                        onGenerateChronologyClick = onGenerateChronologyClick
                    )
                }
            }
        }
    }

    if (showTagDialog) {
        CaseTagEditorDialog(
            currentTags = currentMatterTags,
            onDismiss = { showTagDialog = false },
            onSaveTags = { newTags ->
                onUpdateMatterTags?.invoke(matter.id, newTags)
                showTagDialog = false
            }
        )
    }
}

// ==================== 1. OVERVIEW TAB ====================

@Composable
fun OverviewTabContent(
    matter: MatterDto,
    deadlines: List<DeadlineDto> = emptyList(),
    hearings: List<HearingDto> = emptyList(),
    cachedAnalysis: CachedCaseAnalysis?,
    isAnalyzing: Boolean,
    onEditMatterClick: () -> Unit,
    onUploadDocsClick: () -> Unit,
    onScanDocumentClick: (() -> Unit)? = null,
    onAnalyzeCaseClick: () -> Unit,
    onConfirmDeadline: ((String) -> Unit)? = null,
    onProposeDeadlineClick: (() -> Unit)? = null,
    onOpenWebSource: (String) -> Unit,
    isSummarizing: Boolean = false,
    onSummarizeCaseClick: () -> Unit = {}
) {
    val colors = LocalHoyaamColors.current
    val hasSubject = !matter.subject.isNullOrBlank()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Procedural & Deadline Timeline Visualizer
        item {
            LitigationTimelineChart(
                matter = matter,
                deadlines = deadlines,
                hearings = hearings,
                onConfirmDeadline = onConfirmDeadline,
                onProposeDeadlineClick = onProposeDeadlineClick
            )
        }

        // Subject Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "موضوع القضية",
                            color = colors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AmiriFontFamily
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onEditMatterClick,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(if (hasSubject) "تعديل البيانات" else "+ إضافة الموضوع", fontSize = 12.sp)
                            }

                            if (onScanDocumentClick != null) {
                                OutlinedButton(
                                    onClick = onScanDocumentClick,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مسح بالكاميرا", fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = onUploadDocsClick,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.text),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("رفع مستندات", fontSize = 12.sp)
                            }
                        }
                    }

                    // litigation-summarize (whole-case scope) — real
                    // function, previously never wired into this app.
                    // Ephemeral, same as chronology: not persisted
                    // server-side, shown once via SummaryResultDialog.
                    OutlinedButton(
                        onClick = onSummarizeCaseClick,
                        enabled = !isSummarizing,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).padding(top = 8.dp)
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جارٍ التلخيص…", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Outlined.Summarize, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تلخيص تنفيذي للقضية", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (hasSubject) {
                        Text(
                            text = matter.subject!!,
                            color = colors.text,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.warn.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, colors.warn.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لا يوجد موضوع مسجّل لهذه القضية بعد — اكتبه كنص حر عبر «تعديل البيانات»، أو ارفع مستندات القضية وسيستند التحليل بالذكاء الاصطناعي إليها.",
                                color = colors.warn,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp),
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                }
            }
        }

        // AI Case Analysis Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تحليل القضية بالذكاء الاصطناعي",
                                color = colors.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AmiriFontFamily
                            )
                        }

                        Button(
                            onClick = onAnalyzeCaseClick,
                            enabled = hasSubject && !isAnalyzing,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جارٍ التحليل…", fontSize = 12.sp)
                            } else {
                                Text("تحليل", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "يُبنى عند الطلب من مستندات هذه القضية المستخرجة (كمصدر وحيد للوقائع) ومن المصادر القانونية المطابقة للموضوع (كمصدر وحيد للاستشهاد) وبحث الويب لخطة العمل.",
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = ArabicSansFontFamily,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    if (!hasSubject) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.warn.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, colors.warn.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "أدخل الموضوع أولاً (أعلاه) لتفعيل التحليل.",
                                color = colors.warn,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp),
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }

                    if (cachedAnalysis != null) {
                        val analysis = cachedAnalysis.result
                        val timeStr = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.US).format(Date(cachedAnalysis.generatedAtEpochMs))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.inset,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "نتيجة التحليل الأخير",
                                        color = colors.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("تم التحليل في: ", color = colors.textDim, fontSize = 11.sp)
                                        BidiMonoText(text = timeStr, color = colors.text2, fontSize = 11.sp)
                                    }
                                }

                                if (!analysis.note.isNullOrEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.warn.copy(alpha = 0.1f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(analysis.note, color = colors.warn, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                                    }
                                }

                                if (!analysis.analysis.isNullOrEmpty()) {
                                    Text(
                                        text = analysis.analysis,
                                        color = colors.text,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        fontFamily = ArabicSansFontFamily,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }

                                if (analysis.caseContext != null) {
                                    Text(
                                        text = "مبني على ${analysis.caseContext.pagesUsed} صفحة من ${analysis.caseContext.documentsUsed} مستند لهذه القضية، و${analysis.matches.size} مقطع قانوني.",
                                        color = colors.textDim,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }

                                // Verified Legal Authorities
                                if (analysis.matches.isNotEmpty()) {
                                    Text(
                                        text = "المصادر القانونية المعتمدة:",
                                        color = colors.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 10.dp)
                                    )
                                    analysis.matches.forEach { match ->
                                        Row(
                                            modifier = Modifier.padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("• ${match.title} (${match.citation})", color = colors.text2, fontSize = 12.sp)
                                            match.verificationStatus?.let { status ->
                                                if (status == "human_verified") ConfirmedChip(text = "موثّق")
                                                else ProvisionalChip(text = "غير موثّق")
                                            }
                                        }
                                    }
                                }

                                // External Web Sources (Roadmap Only)
                                if (analysis.webSources.isNotEmpty()) {
                                    HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 10.dp))
                                    Text(
                                        text = "مصادر خارجية استُخدمت لقسم خطة العمل فقط (غير موثّقة من المكتب — تحقق بنفسك):",
                                        color = colors.danger,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    analysis.webSources.forEach { source ->
                                        source.uri?.let { uri ->
                                            Text(
                                                text = "🔗 ${source.title ?: uri}",
                                                color = colors.accent,
                                                fontSize = 12.sp,
                                                modifier = Modifier
                                                    .clickable { onOpenWebSource(uri) }
                                                    .padding(vertical = 2.dp)
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
    }
}

// ==================== 2. PARTIES TAB ====================

val PARTY_ROLE_LABELS_MAP = mapOf(
    "plaintiff" to "مدعي",
    "defendant" to "مدعى عليه",
    "third_party" to "خصم متدخل",
    "counsel_own_side" to "محامي الموكل",
    "counsel_opposing" to "محامي الخصم"
)

@Composable
fun PartiesTabContent(
    parties: List<PartyDto>,
    onAddPartyClick: () -> Unit,
    onEditPartyClick: (PartyDto) -> Unit,
    onDeletePartyClick: (String) -> Unit,
    onCheckConflictsClick: () -> Unit = {}
) {
    val colors = LocalHoyaamColors.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "الأطراف في الدعوى",
                        color = colors.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                    Text(
                        text = "يُستخرج الأطراف تلقائياً من المستندات، وهذه القائمة قابلة للتعديل يدوياً.",
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = ArabicSansFontFamily
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCheckConflictsClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فحص تعارض المصالح", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onAddPartyClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة طرف", fontSize = 13.sp)
                    }
                }
            }
        }

        if (parties.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = "لا يوجد أطراف مسجّلون بعد.",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        } else {
            items(parties, key = { it.id }) { party ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
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
                                    text = party.name,
                                    color = colors.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ArabicSansFontFamily
                                )
                                StageChip(text = PARTY_ROLE_LABELS_MAP[party.partyRole] ?: party.partyRole)
                            }

                            if (!party.identifier.isNullOrEmpty()) {
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("الرقم التعريفي: ", color = colors.textDim, fontSize = 12.sp)
                                    BidiMonoText(text = party.identifier, color = colors.text2, fontSize = 12.sp)
                                }
                            }

                            if (!party.notes.isNullOrEmpty()) {
                                Text(
                                    text = party.notes,
                                    color = colors.textDim,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onEditPartyClick(party) }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = colors.text2, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeletePartyClick(party.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = colors.danger, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 3. HEARINGS TAB ====================

@Composable
fun HearingsTabContent(
    hearings: List<HearingDto>,
    matterLabel: String = "قضية",
    court: String? = null,
    onRecordHearingClick: () -> Unit,
    onSyncHearingToCalendar: ((HearingDto) -> Unit)? = null
) {
    val colors = LocalHoyaamColors.current
    val context = LocalContext.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "سجل الجلسات القضائية",
                        color = colors.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                    Text(
                        text = "جدول مواعيد الجلسات والقرارات مع المزامنة مع التقويم",
                        color = colors.textDim,
                        fontSize = 11.sp,
                        fontFamily = ArabicSansFontFamily
                    )
                }

                Button(
                    onClick = onRecordHearingClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.text),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تسجيل جلسة", fontSize = 13.sp)
                }
            }
        }

        if (hearings.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = "لا توجد جلسات مسجّلة بعد لهذه القضية.",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        } else {
            items(hearings, key = { it.id }) { hearing ->
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BidiMonoText(text = hearing.sessionDate, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                if (!hearing.sessionTime.isNullOrEmpty()) {
                                    BidiMonoText(text = hearing.sessionTime.take(5), color = colors.textDim, fontSize = 12.sp)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                hearing.outcome?.let { outcome ->
                                    val label = when (outcome) {
                                        "adjourned" -> "تأجيل"
                                        "reserved_for_judgment" -> "حجز للحكم"
                                        "judgment_issued" -> "صدور حكم"
                                        else -> outcome
                                    }
                                    StageChip(text = label)
                                }

                                IconButton(
                                    onClick = {
                                        if (onSyncHearingToCalendar != null) {
                                            onSyncHearingToCalendar(hearing)
                                        } else {
                                            try {
                                                val intent = com.example.util.CalendarSyncManager.createHearingCalendarIntent(hearing, matterLabel, court)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "تعذر فتح التقويم", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.CalendarMonth,
                                        contentDescription = "إضافة للتقويم",
                                        tint = colors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (!hearing.adjournmentReason.isNullOrEmpty()) {
                            Text(
                                text = "القرار / سبب التأجيل: ${hearing.adjournmentReason}",
                                color = colors.text,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 6.dp),
                                fontFamily = ArabicSansFontFamily
                            )
                        }

                        if (!hearing.nextSessionDate.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الجلسة القادمة: ", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                BidiMonoText(text = hearing.nextSessionDate, color = colors.accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 4. DEADLINES TAB ====================

@Composable
fun DeadlinesTabContent(
    deadlines: List<DeadlineDto>,
    matter: MatterDto? = null,
    hearings: List<HearingDto> = emptyList(),
    onProposeDeadlineClick: () -> Unit,
    onConfirmDeadlineClick: (String) -> Unit,
    onSyncDeadlineToCalendar: ((DeadlineDto) -> Unit)? = null
) {
    val colors = LocalHoyaamColors.current
    val context = LocalContext.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (matter != null) {
            item {
                LitigationTimelineChart(
                    matter = matter,
                    deadlines = deadlines,
                    hearings = hearings,
                    onConfirmDeadline = onConfirmDeadlineClick,
                    onProposeDeadlineClick = onProposeDeadlineClick
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "جدول المواعيد النظامية المفصل",
                        color = colors.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                    Text(
                        text = "يُحسب التاريخ آلياً ويبقى «مبدئي» حتى يؤكده محامٍ.",
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = ArabicSansFontFamily
                    )
                }

                Button(
                    onClick = onProposeDeadlineClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.text),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("اقتراح موعد", fontSize = 13.sp)
                }
            }
        }

        if (deadlines.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = "لا توجد مواعيد بعد لهذه القضية.",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        } else {
            items(deadlines, key = { it.id }) { deadline ->
                val isProvisional = deadline.status == "provisional"
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, if (isProvisional) colors.warn else colors.good)
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
                                if (isProvisional) ProvisionalChip(text = "مبدئي")
                                else ConfirmedChip(text = "مؤكد")
                            }

                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الاستحقاق: ", color = colors.textDim, fontSize = 12.sp)
                                BidiMonoText(text = deadline.computedDueDate, color = colors.text2, fontSize = 13.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = {
                                    if (onSyncDeadlineToCalendar != null) {
                                        onSyncDeadlineToCalendar(deadline)
                                    } else {
                                        try {
                                            val intent = com.example.util.CalendarSyncManager.createDeadlineCalendarIntent(
                                                deadline,
                                                matter?.matterLabel ?: "قضية",
                                                matter?.court
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "تعذر فتح التقويم", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
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
                                    onClick = { onConfirmDeadlineClick(deadline.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.good),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("تأكيد الميعاد", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 5. DOCUMENTS TAB ====================

@Composable
fun DocumentsTabContent(
    documents: List<DocumentDto>,
    localScans: List<LocalScannedDocumentEntity> = emptyList(),
    onUploadDocsClick: () -> Unit,
    onScanDocumentClick: (() -> Unit)? = null,
    onOpenDocument: (DocumentDto) -> Unit,
    onDeleteLocalScan: ((String) -> Unit)? = null,
    onUpdateScanTags: ((String, String) -> Unit)? = null,
    isSummarizing: Boolean = false,
    onSummarizeDocumentClick: (String) -> Unit = {}
) {
    val colors = LocalHoyaamColors.current
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var scanForTagEdit by remember { mutableStateOf<Pair<String, String>?>(null) }

    val docCategoryTags = listOf("الكل", "Court Filing", "Client Correspondence", "Contracts", "Pleadings", "Evidence")

    val filteredScans = remember(localScans, selectedTagFilter) {
        if (selectedTagFilter == null || selectedTagFilter == "الكل") localScans
        else localScans.filter { it.tags.contains(selectedTagFilter ?: "", ignoreCase = true) || it.docType.contains(selectedTagFilter ?: "", ignoreCase = true) }
    }

    val filteredDocs = remember(documents, selectedTagFilter) {
        if (selectedTagFilter == null || selectedTagFilter == "الكل") documents
        else documents.filter {
            it.originalFilename?.contains(selectedTagFilter ?: "", ignoreCase = true) == true ||
            it.mimeType?.contains(selectedTagFilter ?: "", ignoreCase = true) == true
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مستندات القضية والمسح الضوئي",
                    color = colors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onScanDocumentClick != null) {
                        OutlinedButton(
                            onClick = onScanDocumentClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مسح بالكاميرا", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = onUploadDocsClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("رفع مستندات", fontSize = 12.sp)
                    }
                }
            }
        }

        // Tag Filter Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                docCategoryTags.forEach { tag ->
                    val isSelected = (selectedTagFilter == null && tag == "الكل") || selectedTagFilter == tag
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTagFilter = if (tag == "الكل") null else tag },
                        label = { Text(getTagDisplayLabel(tag), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.text,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Local Scans Section if any
        if (filteredScans.isNotEmpty()) {
            item {
                Text(
                    text = "المستندات الممسوحة ضوئياً بالكاميرا (مخزنة ومصنفة محلياً)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    fontFamily = ArabicSansFontFamily,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(filteredScans, key = { it.id }) { scan ->
                val scanTagsList = scan.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AsyncImage(
                                    model = scan.imagePath,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.inset)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = scan.title,
                                        color = colors.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ArabicSansFontFamily
                                    )
                                    Text(
                                        text = "النوع: ${scan.docType} • مسح ضوئي محلي",
                                        color = colors.textDim,
                                        fontSize = 11.sp,
                                        fontFamily = ArabicSansFontFamily
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { scanForTagEdit = scan.id to scan.tags },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Outlined.Label, contentDescription = "تعديل الوسوم", tint = colors.textDim, modifier = Modifier.size(16.dp))
                                }

                                if (onDeleteLocalScan != null) {
                                    IconButton(onClick = { onDeleteLocalScan(scan.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = colors.danger.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // Tags list for scan
                        if (scanTagsList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                scanTagsList.forEach { tag ->
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
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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

        if (filteredDocs.isEmpty() && filteredScans.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = if (selectedTagFilter != null) "لا توجد مستندات تحت هذا التصنيف." else "لا توجد مستندات بعد. يمكنك رفع ملفات PDF أو تصوير المستندات بالماسح الضوئي.",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        } else if (filteredDocs.isNotEmpty()) {
            items(filteredDocs, key = { it.id }) { doc ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenDocument(doc) },
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = colors.accent)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = doc.originalFilename ?: "مستند قضائي",
                                    color = colors.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ArabicSansFontFamily
                                )
                                doc.createdAt?.let { date ->
                                    BidiMonoText(text = date.take(10), color = colors.textDim, fontSize = 11.sp)
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (doc.ocrStatus == "done") {
                                IconButton(
                                    onClick = { onSummarizeDocumentClick(doc.id) },
                                    enabled = !isSummarizing,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Summarize,
                                        contentDescription = "تلخيص المستند",
                                        tint = colors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            when (doc.ocrStatus) {
                                "done" -> ConfirmedChip(text = "تم الاستخراج")
                                "failed" -> DangerChip(text = "فشل")
                                else -> ProvisionalChip(text = doc.ocrStatus)
                            }
                        }
                    }
                }
            }
        }
    }

    scanForTagEdit?.let { (scanId, currentTags) ->
        CaseTagEditorDialog(
            currentTags = currentTags,
            onDismiss = { scanForTagEdit = null },
            onSaveTags = { newTags ->
                onUpdateScanTags?.invoke(scanId, newTags)
                scanForTagEdit = null
            }
        )
    }
}

// ==================== 6. DRAFTS TAB ====================

@Composable
fun DraftsTabContent(
    drafts: List<DraftDto>,
    onNewDraftClick: () -> Unit,
    onNewMemoClick: () -> Unit,
    onFillTemplateClick: () -> Unit,
    onInspectCitationsClick: (DraftDto) -> Unit,
    onExportDraftClick: (String) -> Unit,
    isGeneratingBriefing: Boolean = false,
    onGenerateBriefingClick: () -> Unit = {}
) {
    val colors = LocalHoyaamColors.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text(
                    text = "المسودات والمذكرات القانونية",
                    color = colors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )
                Text(
                    text = "توليد مذكرات ومسودات مدعومة بالاستشهادات القانونية الموثّقة مع تصدير Word (.docx)",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    fontFamily = ArabicSansFontFamily,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNewDraftClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("+ مسودة", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onNewMemoClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("+ مذكرة قانونية", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onFillTemplateClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("+ ملء قالب", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onGenerateBriefingClick,
                    enabled = !isGeneratingBriefing,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    if (isGeneratingBriefing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جارٍ توليد الإحاطة التحضيرية…", fontSize = 12.sp, color = Color.White)
                    } else {
                        Icon(Icons.Outlined.Gavel, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("توليد إحاطة تحضيرية للجلسة القادمة", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (drafts.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = "لا توجد مسودات أو مذكرات بعد لهذه القضية.",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        } else {
            items(drafts, key = { it.id }) { draft ->
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (draft.docType == "legal_memo") "مذكرة قانونية" else draft.docType,
                                    color = colors.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                StageChip(text = "نسخة ${draft.version}")
                            }

                            ConfirmedChip(text = draft.status)
                        }

                        if (!draft.contentText.isNullOrEmpty()) {
                            Text(
                                text = draft.contentText,
                                color = colors.text,
                                fontSize = 13.sp,
                                maxLines = 3,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onInspectCitationsClick(draft) }) {
                                Text("فحص الاستشهادات", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onExportDraftClick(draft.id) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.text),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("تصدير Word", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 7. CHRONOLOGY TAB ====================

@Composable
fun ChronologyTabContent(
    chronology: ChronologyResponse?,
    isGenerating: Boolean,
    onGenerateChronologyClick: () -> Unit
) {
    val colors = LocalHoyaamColors.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "التسلسل الزمني لأحداث القضية",
                        color = colors.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                    Text(
                        text = "يُبنى عند الطلب من نصوص المستندات المستخرجة مع إشارة دقيقة لرقم الصفحة.",
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = ArabicSansFontFamily
                    )
                }

                Button(
                    onClick = onGenerateChronologyClick,
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جارٍ التوليد…", fontSize = 12.sp)
                    } else {
                        Text("توليد التسلسل", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (chronology == null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = "اضغط «توليد التسلسل» لتجميع وقائع القضية وتواريخها من ملفات المستندات.",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        } else if (chronology.events.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = "لا توجد أحداث مستخرجة من المستندات.",
                        color = colors.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                        fontFamily = ArabicSansFontFamily
                    )
                }
            }
        } else {
            items(chronology.events) { event ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        BidiMonoText(
                            text = event.date ?: "—",
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(95.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.description,
                                color = colors.text,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontFamily = ArabicSansFontFamily
                            )

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.inset,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text(
                                    text = "صفحة ${event.sourcePageNumber}",
                                    color = colors.textDim,
                                    fontSize = 11.sp,
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

// ==================== 8. ROOM DATABASE NOTES & SUMMARY TAB ====================

val NOTE_TAGS = listOf("عام", "مرافعة", "ملاحظة جلسة", "استراتيجية دفاع", "مراجعة مستندات", "تنبيه موعد")

@Composable
fun NotesTabContent(
    matter: MatterDto,
    localSummary: LocalCaseSummaryEntity?,
    notes: List<LocalCaseNoteEntity>,
    scans: List<LocalScannedDocumentEntity> = emptyList(),
    hearings: List<HearingDto> = emptyList(),
    deadlines: List<DeadlineDto> = emptyList(),
    onAddNote: (String, String, String, Boolean) -> Unit,
    onTogglePinNote: (Long, Boolean) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onScanDocumentClick: (() -> Unit)? = null,
    onDeleteScan: ((String) -> Unit)? = null
) {
    val colors = LocalHoyaamColors.current
    val context = LocalContext.current
    var showAddNoteDialog by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // PDF Export Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf_export_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "PDF",
                                    tint = colors.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "تصدير وطباعة ملف القضية PDF",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text,
                                    fontFamily = AmiriFontFamily
                                )
                                Text(
                                    text = "توليد تقرير رسمي منسق يشمل المذكرات، الملخص، والمستندات الممسوحة",
                                    fontSize = 11.sp,
                                    color = colors.textDim,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val pdfFile = PdfExporter.generateCasePdf(
                                    context = context,
                                    matter = matter,
                                    summary = localSummary,
                                    notes = notes,
                                    scans = scans,
                                    hearings = hearings,
                                    deadlines = deadlines
                                )
                                if (pdfFile != null) {
                                    Toast.makeText(context, "تم إنشاء التقرير بنجاح: ${pdfFile.name}", Toast.LENGTH_SHORT).show()
                                    PdfExporter.sharePdf(context, pdfFile)
                                } else {
                                    Toast.makeText(context, "فشل إنشاء ملف الـ PDF", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_share_pdf_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.text),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مشاركة PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                        }

                        OutlinedButton(
                            onClick = {
                                val pdfFile = PdfExporter.generateCasePdf(
                                    context = context,
                                    matter = matter,
                                    summary = localSummary,
                                    notes = notes,
                                    scans = scans,
                                    hearings = hearings,
                                    deadlines = deadlines
                                )
                                if (pdfFile != null) {
                                    PdfExporter.printPdf(context, pdfFile)
                                } else {
                                    Toast.makeText(context, "فشل تجهيز أمر الطباعة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("print_pdf_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.accent),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("طباعة التقرير", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                        }
                    }
                }
            }
        }

        // Room Local Offline Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("offline_summary_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.heroBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = colors.heroText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ملخص القضية المخزن محلياً (Room)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text,
                                    fontFamily = AmiriFontFamily
                                )
                                Text(
                                    text = "متاح ومحفوظ على الجهاز بدون اتصال بالإنترنت",
                                    fontSize = 11.sp,
                                    color = colors.textDim,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = colors.good.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, colors.good.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.good)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "متزامن محلياً ✓",
                                    fontSize = 10.sp,
                                    color = colors.good,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Summary Details
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.inset
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = localSummary?.summaryText ?: matter.subject ?: "قضية قيد المباشرة والمتابعة الإجرائية لدى الدائرة المختصة.",
                                fontSize = 13.sp,
                                color = colors.text,
                                lineHeight = 20.sp,
                                fontFamily = ArabicSansFontFamily
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "الجلسات المسجلة: ${localSummary?.totalHearingsCount ?: 0}",
                                    fontSize = 11.sp,
                                    color = colors.text2,
                                    fontFamily = ArabicSansFontFamily
                                )
                                Text(
                                    text = "المواعيد الحتمية: ${localSummary?.pendingDeadlinesCount ?: 0}",
                                    fontSize = 11.sp,
                                    color = colors.text2,
                                    fontFamily = ArabicSansFontFamily
                                )
                                localSummary?.nextSessionDate?.let { nextDate ->
                                    Text(
                                        text = "الجلسة القادمة: $nextDate",
                                        fontSize = 11.sp,
                                        color = colors.accent,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ArabicSansFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Toolbar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مذكرات وملاحظات المحامي (${notes.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    fontFamily = AmiriFontFamily
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onScanDocumentClick != null) {
                        OutlinedButton(
                            onClick = onScanDocumentClick,
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مسح مستند", fontSize = 12.sp, fontFamily = ArabicSansFontFamily)
                        }
                    }

                    Button(
                        onClick = { showAddNoteDialog = true },
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_note_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ملاحظة جديدة", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                    }
                }
            }
        }

        // Notes List
        if (notes.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            tint = colors.textDim,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا توجد ملاحظات محلية مسجلة لهذه القضية بعد.",
                            color = colors.text2,
                            fontSize = 13.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                        Text(
                            text = "سجل نقاط المرافعة، ملاحظات الجلسة السرية، والبنود القانونية للرجوع إليها دون إنترنت.",
                            color = colors.textDim,
                            fontSize = 11.sp,
                            fontFamily = ArabicSansFontFamily,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(notes, key = { it.id }) { note ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_card_${note.id}"),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.card,
                    border = BorderStroke(
                        width = if (note.isPinned) 1.5.dp else 1.dp,
                        color = if (note.isPinned) colors.accent else colors.border
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (note.isPinned) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "مثبتة",
                                        tint = colors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = note.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.heroBg
                                ) {
                                    Text(
                                        text = note.tag,
                                        fontSize = 10.sp,
                                        color = colors.heroText,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontFamily = ArabicSansFontFamily
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { onTogglePinNote(note.id, note.isPinned) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = "تثبيت",
                                        tint = if (note.isPinned) colors.accent else colors.textDim,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteNote(note.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف",
                                        tint = colors.danger.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = note.content,
                            fontSize = 13.sp,
                            color = colors.text,
                            lineHeight = 20.sp,
                            fontFamily = ArabicSansFontFamily
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
                            Text(
                                text = "حُفظت: ${sdf.format(Date(note.updatedAt))}",
                                fontSize = 10.sp,
                                color = colors.textDim,
                                fontFamily = ArabicSansFontFamily
                            )

                            Text(
                                text = "محفوظة بقاعدة بيانات Room المحلية",
                                fontSize = 10.sp,
                                color = colors.good,
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Note Dialog
    if (showAddNoteDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newContent by remember { mutableStateOf("") }
        var selectedTag by remember { mutableStateOf(NOTE_TAGS[0]) }
        var isPinned by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = {
                Text("إضافة ملاحظة لقضية ${matter.matterLabel}", fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("عنوان الملاحظة") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_title_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("نص الملاحظة أو النقطة القانونية") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("note_content_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text("التصنيف:", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NOTE_TAGS.take(3).forEach { tag ->
                            val isSel = selectedTag == tag
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSel) colors.heroBg else colors.inset,
                                border = BorderStroke(1.dp, if (isSel) colors.accent else colors.border),
                                modifier = Modifier.clickable { selectedTag = tag }
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 11.sp,
                                    color = if (isSel) colors.heroText else colors.text2,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPinned, onCheckedChange = { isPinned = it })
                        Text("تثبيت الملاحظة في الأعلى", fontSize = 12.sp, fontFamily = ArabicSansFontFamily)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank() && newContent.isNotBlank()) {
                            onAddNote(newTitle, newContent, selectedTag, isPinned)
                            showAddNoteDialog = false
                        }
                    },
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    modifier = Modifier.testTag("save_note_button")
                ) {
                    Text("حفظ الملاحظة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

