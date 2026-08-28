package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.LocalCaseNoteEntity
import com.example.data.local.entities.LocalCaseSummaryEntity
import com.example.data.local.entities.LocalDeadlineEntity
import com.example.data.local.entities.LocalScannedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseSummaryDao {
    @Query("SELECT * FROM case_summaries ORDER BY lastSyncedAt DESC")
    fun getAllSummaries(): Flow<List<LocalCaseSummaryEntity>>

    @Query("SELECT * FROM case_summaries WHERE matterId = :matterId LIMIT 1")
    fun getSummaryByMatterId(matterId: String): Flow<LocalCaseSummaryEntity?>

    @Query("SELECT * FROM case_summaries WHERE matterId = :matterId LIMIT 1")
    suspend fun getSummaryDirect(matterId: String): LocalCaseSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: LocalCaseSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummaries(summaries: List<LocalCaseSummaryEntity>)

    @Query("UPDATE case_summaries SET tags = :tags WHERE matterId = :matterId")
    suspend fun updateMatterTags(matterId: String, tags: String)

    @Query("DELETE FROM case_summaries WHERE matterId = :matterId")
    suspend fun deleteSummary(matterId: String)

    @Query("SELECT * FROM case_summaries WHERE tags LIKE '%' || :tag || '%' ORDER BY lastSyncedAt DESC")
    fun getSummariesByTag(tag: String): Flow<List<LocalCaseSummaryEntity>>

    @Query("SELECT * FROM case_summaries WHERE matterLabel LIKE '%' || :query || '%' OR caseNumber LIKE '%' || :query || '%' OR court LIKE '%' || :query || '%' OR subject LIKE '%' || :query || '%' OR summaryText LIKE '%' || :query || '%' OR aiAnalysisSummary LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY lastSyncedAt DESC")
    fun searchCaseSummaries(query: String): Flow<List<LocalCaseSummaryEntity>>
}

@Dao
interface CaseNoteDao {
    @Query("SELECT * FROM case_notes WHERE matterId = :matterId ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesForMatter(matterId: String): Flow<List<LocalCaseNoteEntity>>

    @Query("SELECT * FROM case_notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<LocalCaseNoteEntity>>

    @Query("SELECT * FROM case_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): LocalCaseNoteEntity?

    @Query("SELECT * FROM case_notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tag LIKE '%' || :query || '%' ORDER BY isPinned DESC, updatedAt DESC")
    fun searchNotes(query: String): Flow<List<LocalCaseNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: LocalCaseNoteEntity): Long

    @Update
    suspend fun updateNote(note: LocalCaseNoteEntity)

    @Query("DELETE FROM case_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("UPDATE case_notes SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)
}

@Dao
interface ScannedDocumentDao {
    @Query("SELECT * FROM scanned_documents WHERE matterId = :matterId ORDER BY createdAt DESC")
    fun getScansForMatter(matterId: String): Flow<List<LocalScannedDocumentEntity>>

    @Query("SELECT * FROM scanned_documents ORDER BY createdAt DESC")
    fun getAllScans(): Flow<List<LocalScannedDocumentEntity>>

    @Query("SELECT * FROM scanned_documents WHERE tags LIKE '%' || :tag || '%' ORDER BY createdAt DESC")
    fun getScansByTag(tag: String): Flow<List<LocalScannedDocumentEntity>>

    @Query("SELECT * FROM scanned_documents WHERE title LIKE '%' || :query || '%' OR docType LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR (ocrPreviewText IS NOT NULL AND ocrPreviewText LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchScans(query: String): Flow<List<LocalScannedDocumentEntity>>

    @Query("UPDATE scanned_documents SET tags = :tags WHERE id = :id")
    suspend fun updateScanTags(id: String, tags: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: LocalScannedDocumentEntity)

    @Query("DELETE FROM scanned_documents WHERE id = :id")
    suspend fun deleteScanById(id: String)
}

@Dao
interface LocalDeadlineDao {
    @Query("SELECT * FROM litigation_deadlines ORDER BY computedDueDate ASC")
    fun getAllDeadlines(): Flow<List<LocalDeadlineEntity>>

    @Query("SELECT * FROM litigation_deadlines WHERE matterId = :matterId ORDER BY computedDueDate ASC")
    fun getDeadlinesForMatter(matterId: String): Flow<List<LocalDeadlineEntity>>

    @Query("SELECT * FROM litigation_deadlines WHERE isSyncedToCalendar = 1")
    fun getSyncedDeadlines(): Flow<List<LocalDeadlineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeadline(deadline: LocalDeadlineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeadlines(deadlines: List<LocalDeadlineEntity>)

    @Query("UPDATE litigation_deadlines SET isSyncedToCalendar = :isSynced, calendarEventId = :calendarEventId, lastSyncedAt = :syncedAt WHERE id = :id")
    suspend fun updateCalendarSyncStatus(id: String, isSynced: Boolean, calendarEventId: Long?, syncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE litigation_deadlines SET status = :status WHERE id = :id")
    suspend fun updateDeadlineStatus(id: String, status: String)

    @Query("DELETE FROM litigation_deadlines WHERE id = :id")
    suspend fun deleteDeadline(id: String)

    @Query("SELECT * FROM litigation_deadlines WHERE triggerEvent LIKE '%' || :query || '%' OR matterLabel LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%'")
    fun searchDeadlines(query: String): Flow<List<LocalDeadlineEntity>>
}
