package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "case_summaries")
data class LocalCaseSummaryEntity(
    @PrimaryKey val matterId: String,
    val matterLabel: String,
    val caseNumber: String? = null,
    val caseYear: Int? = null,
    val court: String? = null,
    val circuit: String? = null,
    val stage: String? = null,
    val status: String = "active",
    val subject: String? = null,
    val summaryText: String? = null,
    val aiAnalysisSummary: String? = null,
    val totalHearingsCount: Int = 0,
    val pendingDeadlinesCount: Int = 0,
    val nextSessionDate: String? = null,
    val tags: String = "Active", // e.g., "Active, Court Filing, Client Correspondence"
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "case_notes")
data class LocalCaseNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matterId: String,
    val title: String,
    val content: String,
    val tag: String = "عام", // e.g., مرافعة, ملاحظة جلسة, استراتيجية, تنبيه
    val isPinned: Boolean = false,
    val isOfflineCreated: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "scanned_documents")
data class LocalScannedDocumentEntity(
    @PrimaryKey val id: String,
    val matterId: String?,
    val title: String,
    val docType: String,
    val imagePath: String,
    val ocrPreviewText: String? = null,
    val tags: String = "", // e.g., "Court Filing, Client Correspondence"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "litigation_deadlines")
data class LocalDeadlineEntity(
    @PrimaryKey val id: String,
    val matterId: String,
    val matterLabel: String,
    val triggerEvent: String,
    val computedDueDate: String,
    val status: String = "provisional",
    val ruleCode: String? = null,
    val notes: String? = null,
    val court: String? = null,
    val calendarEventId: Long? = null,
    val isSyncedToCalendar: Boolean = false,
    val lastSyncedAt: Long? = null
)
