package com.example.data.local

import com.example.data.local.dao.CaseNoteDao
import com.example.data.local.dao.CaseSummaryDao
import com.example.data.local.dao.LocalDeadlineDao
import com.example.data.local.dao.ScannedDocumentDao
import com.example.data.local.entities.LocalCaseNoteEntity
import com.example.data.local.entities.LocalCaseSummaryEntity
import com.example.data.local.entities.LocalDeadlineEntity
import com.example.data.local.entities.LocalScannedDocumentEntity
import com.example.data.model.DeadlineDto
import com.example.data.model.MatterDto
import kotlinx.coroutines.flow.Flow

class LocalCaseRepository(
    private val summaryDao: CaseSummaryDao,
    private val noteDao: CaseNoteDao,
    private val scanDao: ScannedDocumentDao,
    private val deadlineDao: LocalDeadlineDao
) {
    // ==================== SUMMARIES & MATTERS ====================

    fun getAllSummaries(): Flow<List<LocalCaseSummaryEntity>> = summaryDao.getAllSummaries()

    fun searchSummaries(query: String): Flow<List<LocalCaseSummaryEntity>> =
        summaryDao.searchCaseSummaries(query)

    fun getSummariesByTag(tag: String): Flow<List<LocalCaseSummaryEntity>> =
        summaryDao.getSummariesByTag(tag)

    fun getSummaryForMatter(matterId: String): Flow<LocalCaseSummaryEntity?> =
        summaryDao.getSummaryByMatterId(matterId)

    suspend fun saveCaseSummary(
        matter: MatterDto,
        summaryText: String? = null,
        aiAnalysis: String? = null,
        hearingsCount: Int = 0,
        deadlinesCount: Int = 0,
        nextSessionDate: String? = null,
        tags: String = "Active"
    ) {
        val existing = summaryDao.getSummaryDirect(matter.id)
        val finalTags = if (existing != null && existing.tags.isNotBlank()) existing.tags else tags

        val entity = LocalCaseSummaryEntity(
            matterId = matter.id,
            matterLabel = matter.matterLabel,
            caseNumber = matter.caseNumber,
            caseYear = matter.caseYear,
            court = matter.court,
            circuit = matter.circuit,
            stage = matter.stage,
            status = matter.status,
            subject = matter.subject,
            summaryText = summaryText ?: matter.subject,
            aiAnalysisSummary = aiAnalysis,
            totalHearingsCount = hearingsCount,
            pendingDeadlinesCount = deadlinesCount,
            nextSessionDate = nextSessionDate,
            tags = finalTags,
            lastSyncedAt = System.currentTimeMillis()
        )
        summaryDao.upsertSummary(entity)
    }

    suspend fun updateMatterTags(matterId: String, tags: String) {
        summaryDao.updateMatterTags(matterId, tags)
    }

    // ==================== NOTES ====================

    fun getNotesForMatter(matterId: String): Flow<List<LocalCaseNoteEntity>> =
        noteDao.getNotesForMatter(matterId)

    fun getAllNotes(): Flow<List<LocalCaseNoteEntity>> = noteDao.getAllNotes()

    fun searchNotes(query: String): Flow<List<LocalCaseNoteEntity>> =
        noteDao.searchNotes(query)

    suspend fun addNote(
        matterId: String,
        title: String,
        content: String,
        tag: String = "عام",
        isPinned: Boolean = false
    ): Long {
        val note = LocalCaseNoteEntity(
            matterId = matterId,
            title = title.trim(),
            content = content.trim(),
            tag = tag,
            isPinned = isPinned,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return noteDao.insertNote(note)
    }

    suspend fun updateNote(note: LocalCaseNoteEntity) {
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun togglePinNote(id: Long, currentPinState: Boolean) {
        noteDao.setPinned(id, !currentPinState)
    }

    suspend fun deleteNote(id: Long) {
        noteDao.deleteNoteById(id)
    }

    // ==================== SCANNED DOCUMENTS & TAGS ====================

    fun getScansForMatter(matterId: String): Flow<List<LocalScannedDocumentEntity>> =
        scanDao.getScansForMatter(matterId)

    fun getAllScans(): Flow<List<LocalScannedDocumentEntity>> =
        scanDao.getAllScans()

    fun getScansByTag(tag: String): Flow<List<LocalScannedDocumentEntity>> =
        scanDao.getScansByTag(tag)

    fun searchScans(query: String): Flow<List<LocalScannedDocumentEntity>> =
        scanDao.searchScans(query)

    suspend fun saveScan(
        id: String,
        matterId: String?,
        title: String,
        docType: String,
        imagePath: String,
        ocrPreview: String? = null,
        tags: String = "Court Filing"
    ) {
        val scan = LocalScannedDocumentEntity(
            id = id,
            matterId = matterId,
            title = title,
            docType = docType,
            imagePath = imagePath,
            ocrPreviewText = ocrPreview,
            tags = tags,
            createdAt = System.currentTimeMillis()
        )
        scanDao.insertScan(scan)
    }

    suspend fun updateScanTags(id: String, tags: String) {
        scanDao.updateScanTags(id, tags)
    }

    suspend fun deleteScan(id: String) {
        scanDao.deleteScanById(id)
    }

    // ==================== DEADLINES & CALENDAR SYNC ====================

    fun getAllDeadlines(): Flow<List<LocalDeadlineEntity>> =
        deadlineDao.getAllDeadlines()

    fun getDeadlinesForMatter(matterId: String): Flow<List<LocalDeadlineEntity>> =
        deadlineDao.getDeadlinesForMatter(matterId)

    fun getSyncedDeadlines(): Flow<List<LocalDeadlineEntity>> =
        deadlineDao.getSyncedDeadlines()

    suspend fun cacheDeadlines(
        deadlines: List<DeadlineDto>,
        mattersMap: Map<String, String>, // matterId -> matterLabel
        courtsMap: Map<String, String?> = emptyMap()
    ) {
        val entities = deadlines.map { d ->
            LocalDeadlineEntity(
                id = d.id,
                matterId = d.matterId,
                matterLabel = mattersMap[d.matterId] ?: "قضية",
                triggerEvent = d.triggerEvent,
                computedDueDate = d.computedDueDate,
                status = d.status,
                ruleCode = d.ruleId,
                notes = null,
                court = courtsMap[d.matterId],
                calendarEventId = null,
                isSyncedToCalendar = false,
                lastSyncedAt = null
            )
        }
        deadlineDao.upsertDeadlines(entities)
    }

    suspend fun updateCalendarSyncStatus(deadlineId: String, isSynced: Boolean, calendarEventId: Long?) {
        deadlineDao.updateCalendarSyncStatus(deadlineId, isSynced, calendarEventId)
    }

    suspend fun updateDeadlineStatus(deadlineId: String, status: String) {
        deadlineDao.updateDeadlineStatus(deadlineId, status)
    }
}
