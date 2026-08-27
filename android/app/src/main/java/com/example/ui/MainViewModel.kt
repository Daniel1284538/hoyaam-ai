package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.LocalCaseRepository
import com.example.data.local.entities.LocalCaseNoteEntity
import com.example.data.local.entities.LocalCaseSummaryEntity
import com.example.data.local.entities.LocalDeadlineEntity
import com.example.data.local.entities.LocalScannedDocumentEntity
import com.example.data.model.*
import com.example.data.repository.AuthState
import com.example.data.repository.LitigationRepository
import com.example.ui.theme.AppThemeSetting
import com.example.util.CalendarSyncManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = LitigationRepository(application.applicationContext)
    private val database = AppDatabase.getInstance(application)
    val localRepository = LocalCaseRepository(
        summaryDao = database.caseSummaryDao(),
        noteDao = database.caseNoteDao(),
        scanDao = database.scannedDocumentDao(),
        deadlineDao = database.deadlineDao()
    )

    // Every mutation below used to run inside `try { } catch (_: Exception)
    // {}` with nothing surfaced to the UI at all — a failed write (a 403,
    // a validation error, a network drop, the matter-creation bug that
    // used to call a nonexistent function) looked identical to a
    // successful one: the dialog just closed. This replaces that with a
    // real one-off error event every mutation emits on failure, collected
    // by MainActivity's Scaffold and shown as a Snackbar — the same
    // pattern the auth flow already used correctly.
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorEvents: SharedFlow<String> = _errorEvents

    private suspend fun emitError(e: Exception, fallback: String) {
        _errorEvents.emit(e.message?.takeIf { it.isNotBlank() } ?: fallback)
    }

    // Backs the pull-to-refresh gesture on every tab — refreshDashboard()
    // already refreshes everything (matters/alerts/authorities/templates/
    // extractions via repository.refreshAll(), plus dashboard stats and the
    // roll), so one shared flag and one shared action covers all of them.
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val authState: StateFlow<AuthState> = repository.authState
    val matters: StateFlow<List<MatterDto>> = repository.matters
    val urgentAlerts: StateFlow<List<UrgentAlert>> = repository.urgentAlerts
    val authorities: StateFlow<List<AuthorityDto>> = repository.authorities
    val templates: StateFlow<List<TemplateDto>> = repository.templates
    val pendingExtractions: StateFlow<List<ExtractionDto>> = repository.pendingExtractions
    val cachedCaseAnalysis: StateFlow<CachedCaseAnalysis?> = repository.cachedCaseAnalysis

    private val _themeSetting = MutableStateFlow(AppThemeSetting.SYSTEM)
    val themeSetting: StateFlow<AppThemeSetting> = _themeSetting.asStateFlow()

    private val _isDynamicColor = MutableStateFlow(true)
    val isDynamicColor: StateFlow<Boolean> = _isDynamicColor.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleDynamicColor() {
        _isDynamicColor.value = !_isDynamicColor.value
    }

    private val _currentLang = MutableStateFlow("ar")
    val langCode: StateFlow<String> = _currentLang.asStateFlow()

    private val _selectedMatterId = MutableStateFlow<String?>(null)
    val selectedMatterId: StateFlow<String?> = _selectedMatterId.asStateFlow()

    private val _selectedMatter = MutableStateFlow<MatterDto?>(null)
    val selectedMatter: StateFlow<MatterDto?> = _selectedMatter.asStateFlow()

    private val _matterParties = MutableStateFlow<List<PartyDto>>(emptyList())
    val matterParties: StateFlow<List<PartyDto>> = _matterParties.asStateFlow()

    private val _matterHearings = MutableStateFlow<List<HearingDto>>(emptyList())
    val matterHearings: StateFlow<List<HearingDto>> = _matterHearings.asStateFlow()

    private val _matterDeadlines = MutableStateFlow<List<DeadlineDto>>(emptyList())
    val matterDeadlines: StateFlow<List<DeadlineDto>> = _matterDeadlines.asStateFlow()

    private val _matterDocuments = MutableStateFlow<List<DocumentDto>>(emptyList())
    val matterDocuments: StateFlow<List<DocumentDto>> = _matterDocuments.asStateFlow()

    private val _matterDrafts = MutableStateFlow<List<DraftDto>>(emptyList())
    val matterDrafts: StateFlow<List<DraftDto>> = _matterDrafts.asStateFlow()

    // Room Database Observables
    private val _localCaseSummary = MutableStateFlow<LocalCaseSummaryEntity?>(null)
    val localCaseSummary: StateFlow<LocalCaseSummaryEntity?> = _localCaseSummary.asStateFlow()

    private val _localCaseNotes = MutableStateFlow<List<LocalCaseNoteEntity>>(emptyList())
    val localCaseNotes: StateFlow<List<LocalCaseNoteEntity>> = _localCaseNotes.asStateFlow()

    private val _localScannedDocs = MutableStateFlow<List<LocalScannedDocumentEntity>>(emptyList())
    val localScannedDocs: StateFlow<List<LocalScannedDocumentEntity>> = _localScannedDocs.asStateFlow()

    private val _allLocalSummaries = MutableStateFlow<List<LocalCaseSummaryEntity>>(emptyList())
    val allLocalSummaries: StateFlow<List<LocalCaseSummaryEntity>> = _allLocalSummaries.asStateFlow()

    private val _allLocalNotes = MutableStateFlow<List<LocalCaseNoteEntity>>(emptyList())
    val allLocalNotes: StateFlow<List<LocalCaseNoteEntity>> = _allLocalNotes.asStateFlow()

    private val _allLocalScannedDocs = MutableStateFlow<List<LocalScannedDocumentEntity>>(emptyList())
    val allLocalScannedDocs: StateFlow<List<LocalScannedDocumentEntity>> = _allLocalScannedDocs.asStateFlow()

    private val _allLocalDeadlines = MutableStateFlow<List<LocalDeadlineEntity>>(emptyList())
    val allLocalDeadlines: StateFlow<List<LocalDeadlineEntity>> = _allLocalDeadlines.asStateFlow()

    // Local Search Flows
    private val _localSearchQuery = MutableStateFlow("")
    val localSearchQuery: StateFlow<String> = _localSearchQuery.asStateFlow()

    private val _searchSummaries = MutableStateFlow<List<LocalCaseSummaryEntity>>(emptyList())
    val searchSummaries: StateFlow<List<LocalCaseSummaryEntity>> = _searchSummaries.asStateFlow()

    private val _searchNotes = MutableStateFlow<List<LocalCaseNoteEntity>>(emptyList())
    val searchNotes: StateFlow<List<LocalCaseNoteEntity>> = _searchNotes.asStateFlow()

    private val _searchScans = MutableStateFlow<List<LocalScannedDocumentEntity>>(emptyList())
    val searchScans: StateFlow<List<LocalScannedDocumentEntity>> = _searchScans.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isGeneratingChronology = MutableStateFlow(false)
    val isGeneratingChronology: StateFlow<Boolean> = _isGeneratingChronology.asStateFlow()

    private val _chronologyResponse = MutableStateFlow<ChronologyResponse?>(null)
    val chronologyResponse: StateFlow<ChronologyResponse?> = _chronologyResponse.asStateFlow()

    // Roll Calendar State
    private val _selectedRollDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    val selectedRollDate: StateFlow<String> = _selectedRollDate.asStateFlow()

    private val _dayHearings = MutableStateFlow<List<HearingDto>>(emptyList())
    val dayHearings: StateFlow<List<HearingDto>> = _dayHearings.asStateFlow()

    private val _monthHearingDates = MutableStateFlow<Set<String>>(emptySet())
    val monthHearingDates: StateFlow<Set<String>> = _monthHearingDates.asStateFlow()

    // Research State
    private val _isSearchingResearch = MutableStateFlow(false)
    val isSearchingResearch: StateFlow<Boolean> = _isSearchingResearch.asStateFlow()

    private val _researchResult = MutableStateFlow<ResearchResponse?>(null)
    val researchResult: StateFlow<ResearchResponse?> = _researchResult.asStateFlow()

    // Real deadline_rules, fetched for the Propose Deadline dialog — a
    // rule picker, not free text. litigation-propose-deadline requires a
    // real rule_id and computes the due date itself server-side.
    private val _deadlineRules = MutableStateFlow<List<DeadlineRuleDto>>(emptyList())
    val deadlineRules: StateFlow<List<DeadlineRuleDto>> = _deadlineRules.asStateFlow()

    // Real draft_citations for the currently-inspected draft — replaces
    // the old client-side regex approximation entirely.
    private val _draftCitations = MutableStateFlow<List<DraftCitationDto>>(emptyList())
    val draftCitations: StateFlow<List<DraftCitationDto>> = _draftCitations.asStateFlow()
    private val _isLoadingCitations = MutableStateFlow(false)
    val isLoadingCitations: StateFlow<Boolean> = _isLoadingCitations.asStateFlow()

    // Real, server-side archive search (litigation-search-archive) —
    // separate from the local Room search, which stays as its own,
    // clearly-labeled on-device section.
    private val _isSearchingArchive = MutableStateFlow(false)
    val isSearchingArchive: StateFlow<Boolean> = _isSearchingArchive.asStateFlow()
    private val _archiveSearchResult = MutableStateFlow<ArchiveSearchResponse?>(null)
    val archiveSearchResult: StateFlow<ArchiveSearchResponse?> = _archiveSearchResult.asStateFlow()

    private val _exportedDraftUrl = MutableStateFlow<String?>(null)
    val exportedDraftUrl: StateFlow<String?> = _exportedDraftUrl.asStateFlow()
    private val _openedDocumentUrl = MutableStateFlow<String?>(null)
    val openedDocumentUrl: StateFlow<String?> = _openedDocumentUrl.asStateFlow()

    // Three functions the original AI Studio build never wired at all —
    // added on request, not part of the original bug-fix pass.
    private val _isGeneratingBriefing = MutableStateFlow(false)
    val isGeneratingBriefing: StateFlow<Boolean> = _isGeneratingBriefing.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()
    private val _summaryResult = MutableStateFlow<SummarizeResponse?>(null)
    val summaryResult: StateFlow<SummarizeResponse?> = _summaryResult.asStateFlow()

    private val _isCheckingConflicts = MutableStateFlow(false)
    val isCheckingConflicts: StateFlow<Boolean> = _isCheckingConflicts.asStateFlow()
    private val _conflictCheckResult = MutableStateFlow<ConflictCheckResponse?>(null)
    val conflictCheckResult: StateFlow<ConflictCheckResponse?> = _conflictCheckResult.asStateFlow()

    private val _dashboardStats = MutableStateFlow(
        DashboardStatsData(
            todayHearingsCount = 0,
            todayHearings = emptyList(),
            weekHearingsCount = 0,
            overdueDeadlinesCount = 0,
            pendingExtractionsCount = 0,
            activeTemplatesCount = 0,
            topProvisionalDeadlines = emptyList()
        )
    )
    val dashboardStats: StateFlow<DashboardStatsData> = _dashboardStats.asStateFlow()

    init {
        viewModelScope.launch {
            if (repository.currentSession != null) {
                refreshDashboard()
                loadDeadlineRules()
            }
        }
        viewModelScope.launch {
            localRepository.getAllSummaries().collect { summaries ->
                _allLocalSummaries.value = summaries
            }
        }
        viewModelScope.launch {
            localRepository.getAllNotes().collect { notes ->
                _allLocalNotes.value = notes
            }
        }
        viewModelScope.launch {
            localRepository.getAllScans().collect { scans ->
                _allLocalScannedDocs.value = scans
            }
        }
        viewModelScope.launch {
            localRepository.getAllDeadlines().collect { deadlines ->
                _allLocalDeadlines.value = deadlines
            }
        }
    }

    fun setThemeSetting(setting: AppThemeSetting) {
        _themeSetting.value = setting
        _isDarkTheme.value = (setting == AppThemeSetting.DARK)
    }

    fun cycleThemeSetting() {
        val next = when (_themeSetting.value) {
            AppThemeSetting.SYSTEM -> AppThemeSetting.DARK
            AppThemeSetting.DARK -> AppThemeSetting.LIGHT
            AppThemeSetting.LIGHT -> AppThemeSetting.SYSTEM
        }
        setThemeSetting(next)
    }

    fun toggleTheme() {
        cycleThemeSetting()
    }

    fun setLocalSearchQuery(query: String) {
        _localSearchQuery.value = query
        if (query.isBlank()) {
            _searchSummaries.value = emptyList()
            _searchNotes.value = emptyList()
            _searchScans.value = emptyList()
        } else {
            viewModelScope.launch {
                localRepository.searchSummaries(query).collect { list ->
                    _searchSummaries.value = list
                }
            }
            viewModelScope.launch {
                localRepository.searchNotes(query).collect { list ->
                    _searchNotes.value = list
                }
            }
            viewModelScope.launch {
                localRepository.searchScans(query).collect { list ->
                    _searchScans.value = list
                }
            }
        }
    }

    fun toggleLang() {
        _currentLang.value = if (_currentLang.value == "ar") "en" else "ar"
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _dashboardStats.value = repository.getDashboardStats()
                loadRollForDate(_selectedRollDate.value)

                // Sync all matter summaries & deadlines into Room
                val currentMatters = repository.matters.value
                val mattersMap = currentMatters.associate { it.id to it.matterLabel }
                val courtsMap = currentMatters.associate { it.id to it.court }

                // Cache top provisional and overdue deadlines
                val deadlinesList = _dashboardStats.value.topProvisionalDeadlines
                if (deadlinesList.isNotEmpty()) {
                    localRepository.cacheDeadlines(deadlinesList, mattersMap, courtsMap)
                }

                currentMatters.forEach { m ->
                    localRepository.saveCaseSummary(
                        matter = m,
                        tags = if (m.status == "archived") "Archived" else "Active, Court Filing"
                    )
                }
            } catch (e: Exception) {
                // Was silently swallowed before — a failure here left every
                // list (matters included) stuck empty with no way to tell
                // why. Surface it like every other mutation in this class does.
                emitError(e, "تعذّر تحديث البيانات")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectRollDate(date: String) {
        _selectedRollDate.value = date
        loadRollForDate(date)
    }

    private fun loadRollForDate(date: String) {
        viewModelScope.launch {
            try {
                _dayHearings.value = repository.getHearingsForDate(date)
                _monthHearingDates.value = repository.getMonthHearingDates(date.take(7))
            } catch (_: Exception) {}
        }
    }

    fun loadMatter(matterId: String) {
        _selectedMatterId.value = matterId
        viewModelScope.launch {
            try {
                val currentMatter = repository.matters.value.find { it.id == matterId }
                    ?: repository.getMatter(matterId)
                _selectedMatter.value = currentMatter
                val parties = repository.getParties(matterId)
                _matterParties.value = parties
                val hearings = repository.getHearings(matterId)
                _matterHearings.value = hearings
                val deadlines = repository.getDeadlines(matterId)
                _matterDeadlines.value = deadlines
                val documents = repository.getDocuments(matterId)
                _matterDocuments.value = documents
                val drafts = repository.getDrafts(matterId)
                _matterDrafts.value = drafts
                _chronologyResponse.value = null

                // Sync with local Room cache
                if (currentMatter != null) {
                    val nextDate = hearings.filter { it.outcome == null || it.outcome.isBlank() }
                        .minByOrNull { it.sessionDate }?.sessionDate
                    localRepository.saveCaseSummary(
                        matter = currentMatter,
                        summaryText = currentMatter.subject,
                        aiAnalysis = repository.cachedCaseAnalysis.value?.result?.analysis,
                        hearingsCount = hearings.size,
                        deadlinesCount = deadlines.count { it.status == "provisional" || it.status == "confirmed" },
                        nextSessionDate = nextDate
                    )

                    // Cache deadlines in Room
                    localRepository.cacheDeadlines(
                        deadlines = deadlines,
                        mattersMap = mapOf(currentMatter.id to currentMatter.matterLabel),
                        courtsMap = mapOf(currentMatter.id to currentMatter.court)
                    )
                }
            } catch (e: Exception) {
                emitError(e, "تعذّر تحميل بيانات القضية")
            }
        }

        // Collect Room Flow for matter notes, summary, and scans
        viewModelScope.launch {
            localRepository.getSummaryForMatter(matterId).collect { summary ->
                _localCaseSummary.value = summary
            }
        }
        viewModelScope.launch {
            localRepository.getNotesForMatter(matterId).collect { notes ->
                _localCaseNotes.value = notes
            }
        }
        viewModelScope.launch {
            localRepository.getScansForMatter(matterId).collect { scans ->
                _localScannedDocs.value = scans
            }
        }
    }

    // ==================== TAGGING MANAGEMENT ====================

    fun updateMatterTags(matterId: String, tags: String) {
        viewModelScope.launch {
            try {
                localRepository.updateMatterTags(matterId, tags)
                // Refresh local summary
                val summary = _localCaseSummary.value
                if (summary != null && summary.matterId == matterId) {
                    _localCaseSummary.value = summary.copy(tags = tags)
                }
            } catch (e: Exception) { emitError(e, "تعذّر تحديث الوسوم") }
        }
    }

    fun updateScanTags(scanId: String, tags: String) {
        viewModelScope.launch {
            try {
                localRepository.updateScanTags(scanId, tags)
            } catch (e: Exception) { emitError(e, "تعذّر تحديث تصنيف المستند") }
        }
    }

    // ==================== CALENDAR SYNC ====================

    fun syncDeadlineToCalendar(
        context: Context,
        deadline: DeadlineDto,
        matterLabel: String,
        court: String?
    ): CalendarSyncManager.SyncResult {
        val result = CalendarSyncManager.syncDeadlineDirect(
            context = context,
            deadlineId = deadline.id,
            triggerEvent = deadline.triggerEvent,
            dueDateStr = deadline.computedDueDate,
            matterLabel = matterLabel,
            court = court,
            status = deadline.status
        )

        if (result.success && result.eventId != null) {
            viewModelScope.launch {
                localRepository.updateCalendarSyncStatus(deadline.id, true, result.eventId)
            }
        }
        return result
    }

    fun syncAllDeadlinesToCalendar(context: Context): Pair<Int, Int> {
        val allDeadlines = _matterDeadlines.value.ifEmpty { _dashboardStats.value.topProvisionalDeadlines }
        val currentMatters = repository.matters.value
        val mattersMap = currentMatters.associate { it.id to it.matterLabel }
        val courtsMap = currentMatters.associate { it.id to it.court }

        val res = CalendarSyncManager.syncAllDeadlines(context, allDeadlines, mattersMap, courtsMap)

        if (res.first > 0) {
            viewModelScope.launch {
                allDeadlines.forEach { d ->
                    localRepository.updateCalendarSyncStatus(d.id, true, null)
                }
            }
        }
        return res
    }

    fun syncHearingToCalendar(
        context: Context,
        hearing: HearingDto,
        matterLabel: String,
        court: String?
    ): CalendarSyncManager.SyncResult {
        return CalendarSyncManager.syncHearingDirect(context, hearing, matterLabel, court)
    }

    // ==================== NOTES & SCANS ====================

    fun addLocalNote(matterId: String, title: String, content: String, tag: String = "عام", isPinned: Boolean = false) {
        viewModelScope.launch {
            try {
                localRepository.addNote(matterId, title, content, tag, isPinned)
            } catch (e: Exception) { emitError(e, "تعذّر حفظ الملاحظة") }
        }
    }

    fun togglePinLocalNote(noteId: Long, currentPinned: Boolean) {
        viewModelScope.launch {
            try {
                localRepository.togglePinNote(noteId, currentPinned)
            } catch (e: Exception) { emitError(e, "تعذّر تحديث التثبيت") }
        }
    }

    fun deleteLocalNote(noteId: Long) {
        viewModelScope.launch {
            try {
                localRepository.deleteNote(noteId)
            } catch (e: Exception) { emitError(e, "تعذّر حذف الملاحظة") }
        }
    }

    // Was uploading a hardcoded placeholder sentence ("Document scanned
    // successfully, verification in progress") as if it were the
    // document's content — the real photographed bytes were captured by
    // the camera, threaded all the way down here as `imageBytes`, and
    // then silently discarded; never read again. Every scan produced a
    // server-side record containing that canned sentence while the real
    // page was thrown away. Fixed: the actual JPEG bytes now go through
    // the same real two-step pipeline (legal-ingest -> signed-URL upload
    // -> legal-extract) every other upload in this app already uses
    // correctly, with the correct image/jpeg mime type — so the server's
    // real OCR (legal-extract) reads the real page, not a fake caption.
    fun saveScannedDocument(scan: LocalScannedDocumentEntity, imageBytes: ByteArray? = null) {
        viewModelScope.launch {
            try {
                localRepository.saveScan(
                    id = scan.id,
                    matterId = scan.matterId,
                    title = scan.title,
                    docType = scan.docType,
                    imagePath = scan.imagePath,
                    ocrPreview = scan.ocrPreviewText,
                    tags = scan.tags.ifBlank { "Court Filing" }
                )
                if (scan.matterId != null && imageBytes != null) {
                    repository.ingestAndUploadFile(
                        matterId = scan.matterId,
                        filename = "${scan.title}.jpg",
                        mimeType = "image/jpeg",
                        bytes = imageBytes
                    )
                    loadMatter(scan.matterId)
                }
            } catch (e: Exception) { emitError(e, "تعذّر رفع المستند الممسوح إلى الخادم — تم حفظه محلياً فقط") }
        }
    }

    fun deleteScannedDocument(scanId: String) {
        viewModelScope.launch {
            try {
                localRepository.deleteScan(scanId)
            } catch (e: Exception) { emitError(e, "تعذّر حذف المسح الضوئي") }
        }
    }

    fun createMatter(matter: MatterDto) {
        viewModelScope.launch {
            try {
                repository.createMatter(matter)
                refreshDashboard()
            } catch (e: Exception) { emitError(e, "تعذّر إنشاء القضية") }
        }
    }

    fun updateMatter(matter: MatterDto) {
        viewModelScope.launch {
            try {
                repository.updateMatter(matter)
                _selectedMatter.value = matter
                refreshDashboard()
            } catch (e: Exception) { emitError(e, "تعذّر حفظ تعديلات القضية") }
        }
    }

    fun addParty(party: PartyDto) {
        viewModelScope.launch {
            try {
                repository.addParty(party)
                loadMatter(party.matterId)
            } catch (e: Exception) { emitError(e, "تعذّر إضافة الطرف") }
        }
    }

    fun updateParty(party: PartyDto) {
        viewModelScope.launch {
            try {
                repository.updateParty(party)
                loadMatter(party.matterId)
            } catch (e: Exception) { emitError(e, "تعذّر حفظ تعديلات الطرف") }
        }
    }

    fun deleteParty(partyId: String) {
        val matterId = _selectedMatterId.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteParty(partyId)
                loadMatter(matterId)
            } catch (e: Exception) { emitError(e, "تعذّر حذف الطرف") }
        }
    }

    fun recordHearing(hearing: HearingDto) {
        viewModelScope.launch {
            try {
                repository.recordHearing(hearing)
                loadMatter(hearing.matterId)
                refreshDashboard()
            } catch (e: Exception) { emitError(e, "تعذّر تسجيل الجلسة") }
        }
    }

    fun loadDeadlineRules() {
        viewModelScope.launch {
            try {
                _deadlineRules.value = repository.getDeadlineRules()
            } catch (e: Exception) { emitError(e, "تعذّر تحميل القواعد الإجرائية المعتمدة") }
        }
    }

    // ruleId now comes from a real deadline_rules row (see loadDeadlineRules
    // above) — the server computes the due date itself, deterministically,
    // from that rule; the client no longer invents a date.
    fun proposeDeadline(ruleId: String, triggerEvent: String, triggerDate: String) {
        val matterId = _selectedMatterId.value ?: return
        viewModelScope.launch {
            try {
                repository.proposeDeadline(matterId, ruleId, triggerEvent, triggerDate)
                loadMatter(matterId)
                refreshDashboard()
            } catch (e: Exception) { emitError(e, "تعذّر اقتراح الموعد") }
        }
    }

    fun confirmDeadline(deadlineId: String) {
        viewModelScope.launch {
            try {
                repository.confirmDeadline(deadlineId)
                localRepository.updateDeadlineStatus(deadlineId, "confirmed")
                _selectedMatterId.value?.let { loadMatter(it) }
                refreshDashboard()
            } catch (e: Exception) { emitError(e, "تعذّر تأكيد الموعد") }
        }
    }

    fun uploadDocument(matterId: String, filename: String, docType: String, text: String) {
        viewModelScope.launch {
            try {
                repository.uploadDocument(matterId, filename, docType, text)
                loadMatter(matterId)
                refreshDashboard()
            } catch (e: Exception) { emitError(e, "تعذّر رفع المستند") }
        }
    }

    // Real function: sign-document-url — the only path to an actual
    // stored file. Sets openedDocumentUrl once the signed URL comes back;
    // MainActivity opens it via an ACTION_VIEW intent. Previously this
    // was a Toast with the filename and nothing else.
    fun openDocument(doc: DocumentDto) {
        viewModelScope.launch {
            try {
                val url = repository.getDocumentSignedUrl(
                    matterId = doc.matterId,
                    bucket = doc.bucketId ?: "matter-documents",
                    storagePath = doc.storagePath ?: return@launch
                )
                _openedDocumentUrl.value = url
            } catch (e: Exception) { emitError(e, "تعذّر فتح المستند") }
        }
    }

    fun clearOpenedDocumentUrl() { _openedDocumentUrl.value = null }

    // Real function: litigation-export-draft — generates the actual
    // citation-bound .docx server-side and returns a signed URL.
    // Previously a Toast saying "Preparing Word file…" that called
    // nothing. The server itself refuses to export while any citation on
    // the draft is unverified/flagged — that error surfaces here exactly
    // as returned, which is the point (see verifyCitation below).
    fun exportDraft(draftId: String) {
        viewModelScope.launch {
            try {
                _exportedDraftUrl.value = repository.exportDraft(draftId)
            } catch (e: Exception) { emitError(e, "تعذّر تصدير المسودة إلى Word") }
        }
    }

    fun clearExportedDraftUrl() { _exportedDraftUrl.value = null }

    fun analyzeCase() {
        val matterId = _selectedMatterId.value ?: return
        viewModelScope.launch {
            try {
                _isAnalyzing.value = true
                repository.analyzeCase(matterId)
            } catch (e: Exception) {
                emitError(e, "تعذّر تحليل القضية")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun generateDraft(docType: String, instructions: String, claims: List<String>, selectedAuthorityIds: List<String>) {
        val matterId = _selectedMatterId.value ?: return
        viewModelScope.launch {
            try {
                repository.generateDraft(matterId, docType, instructions, claims, selectedAuthorityIds)
                loadMatter(matterId)
            } catch (e: Exception) { emitError(e, "تعذّر توليد المسودة") }
        }
    }

    fun fillTemplate(templateId: String, fieldValues: Map<String, String>) {
        val matterId = _selectedMatterId.value ?: return
        viewModelScope.launch {
            try {
                repository.fillTemplate(matterId, templateId, fieldValues)
                loadMatter(matterId)
            } catch (e: Exception) { emitError(e, "تعذّر ملء القالب") }
        }
    }

    fun generateChronology() {
        val matterId = _selectedMatterId.value ?: return
        viewModelScope.launch {
            try {
                _isGeneratingChronology.value = true
                _chronologyResponse.value = repository.generateChronology(matterId)
            } catch (e: Exception) {
                emitError(e, "تعذّر توليد التسلسل الزمني")
            } finally {
                _isGeneratingChronology.value = false
            }
        }
    }

    fun performResearch(query: String) {
        viewModelScope.launch {
            try {
                _isSearchingResearch.value = true
                _researchResult.value = repository.performResearch(query)
            } catch (e: Exception) {
                emitError(e, "تعذّر تنفيذ البحث القانوني")
            } finally {
                _isSearchingResearch.value = false
            }
        }
    }

    // Real function: litigation-search-archive — separate from the
    // device-local Room search the Archive screen already had (which
    // stays, clearly labeled as local). This searches the firm's actual
    // past filings server-side, across every matter the caller can
    // access.
    fun searchArchiveRemote(query: String) {
        if (query.isBlank()) { _archiveSearchResult.value = null; return }
        viewModelScope.launch {
            try {
                _isSearchingArchive.value = true
                _archiveSearchResult.value = repository.searchArchive(query)
            } catch (e: Exception) {
                emitError(e, "تعذّر البحث في أرشيف المكتب")
            } finally {
                _isSearchingArchive.value = false
            }
        }
    }

    fun reviewExtraction(extractionId: String, action: String, correctedValue: String?) {
        viewModelScope.launch {
            try {
                repository.reviewExtraction(extractionId, action, correctedValue)
                refreshDashboard()
            } catch (e: Exception) { emitError(e, "تعذّر حفظ قرار المراجعة") }
        }
    }

    fun addAuthority(authority: AuthorityDto) {
        viewModelScope.launch {
            try {
                repository.addAuthority(authority)
            } catch (e: Exception) { emitError(e, "تعذّر إضافة المصدر القانوني") }
        }
    }

    fun verifyAuthority(authorityId: String) {
        viewModelScope.launch {
            try {
                repository.verifyAuthority(authorityId)
            } catch (e: Exception) { emitError(e, "تعذّر توثيق المصدر") }
        }
    }

    fun addTemplate(template: TemplateDto) {
        viewModelScope.launch {
            try {
                repository.addTemplate(template)
                refreshDashboard()
            } catch (e: Exception) { emitError(e, "تعذّر إضافة القالب") }
        }
    }

    // ==================== CITATION INSPECTOR (real data) ====================
    // Replaces the old client-side regex approximation with the real
    // draft_citations rows, bound server-side to actual retrieved
    // passages — and wires the real verify/flag action
    // (litigation-verify-citation), which was never called before.

    fun loadDraftCitations(draftId: String) {
        viewModelScope.launch {
            try {
                _isLoadingCitations.value = true
                _draftCitations.value = repository.getDraftCitations(draftId)
            } catch (e: Exception) {
                emitError(e, "تعذّر تحميل الاستشهادات")
            } finally {
                _isLoadingCitations.value = false
            }
        }
    }

    fun verifyCitation(citationId: String, status: String, draftId: String) {
        viewModelScope.launch {
            try {
                repository.verifyCitation(citationId, status)
                _draftCitations.value = repository.getDraftCitations(draftId)
            } catch (e: Exception) { emitError(e, "تعذّر حفظ قرار المراجعة على الاستشهاد") }
        }
    }

    fun clearDraftCitations() { _draftCitations.value = emptyList() }

    // ==================== HEARING BRIEFING ====================
    // Real function: litigation-hearing-briefing, previously never called
    // anywhere in this app. Persisted server-side as a drafts row, so on
    // success this just reloads the matter — the new briefing shows up
    // in the existing Drafts tab automatically, same as any other draft.
    fun generateHearingBriefing(hearingId: String? = null) {
        val matterId = _selectedMatterId.value ?: return
        viewModelScope.launch {
            try {
                _isGeneratingBriefing.value = true
                val result = repository.generateHearingBriefing(matterId, hearingId)
                if (result.error != null) {
                    _errorEvents.emit(result.error)
                } else {
                    loadMatter(matterId)
                }
            } catch (e: Exception) {
                emitError(e, "تعذّر توليد الإحاطة التحضيرية")
            } finally {
                _isGeneratingBriefing.value = false
            }
        }
    }

    // ==================== SUMMARIZE ====================
    // Real function: litigation-summarize, previously never called
    // anywhere in this app. Ephemeral by design — held only in this
    // state flow, not persisted, same as chronology/case-analysis.
    fun summarizeCase() {
        val matterId = _selectedMatterId.value ?: return
        viewModelScope.launch {
            try {
                _isSummarizing.value = true
                _summaryResult.value = repository.summarize(matterId)
            } catch (e: Exception) {
                emitError(e, "تعذّر تلخيص القضية")
            } finally {
                _isSummarizing.value = false
            }
        }
    }

    fun summarizeDocument(documentId: String) {
        val matterId = _selectedMatterId.value ?: return
        viewModelScope.launch {
            try {
                _isSummarizing.value = true
                _summaryResult.value = repository.summarize(matterId, documentId)
            } catch (e: Exception) {
                emitError(e, "تعذّر تلخيص المستند")
            } finally {
                _isSummarizing.value = false
            }
        }
    }

    fun clearSummaryResult() { _summaryResult.value = null }

    // ==================== CONFLICT CHECK ====================
    // Real function: litigation-check-conflicts, previously never called
    // anywhere in this app. Deliberately searches firm-wide, across
    // matters the caller can't otherwise see — excludeMatterId is only
    // "exclude this matter from the search", never a scope restriction.
    fun checkConflicts(names: List<String>, excludeMatterId: String? = null) {
        val cleanNames = names.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanNames.isEmpty()) return
        viewModelScope.launch {
            try {
                _isCheckingConflicts.value = true
                _conflictCheckResult.value = repository.checkConflicts(cleanNames, excludeMatterId)
            } catch (e: Exception) {
                emitError(e, "تعذّر تنفيذ فحص تعارض المصالح")
            } finally {
                _isCheckingConflicts.value = false
            }
        }
    }

    fun clearConflictCheckResult() { _conflictCheckResult.value = null }
}
