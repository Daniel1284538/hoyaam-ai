package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ==================== AUTH & SESSION ====================

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "user") val user: UserDto? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
    @Json(name = "msg") val msg: String? = null
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String? = null,
    @Json(name = "phone") val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class FactorDto(
    @Json(name = "id") val id: String,
    @Json(name = "friendly_name") val friendlyName: String? = null,
    @Json(name = "factor_type") val factorType: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "totp") val totp: TotpDto? = null
)

@JsonClass(generateAdapter = true)
data class TotpDto(
    @Json(name = "qr_code") val qrCode: String? = null,
    @Json(name = "secret") val secret: String? = null,
    @Json(name = "uri") val uri: String? = null
)

@JsonClass(generateAdapter = true)
data class FactorListResponse(
    @Json(name = "all") val all: List<FactorDto>? = null,
    @Json(name = "totp") val totp: List<FactorDto>? = null
)

@JsonClass(generateAdapter = true)
data class ChallengeResponse(
    @Json(name = "id") val id: String,
    @Json(name = "type") val type: String? = null,
    @Json(name = "expires_at") val expiresAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class AalResponse(
    @Json(name = "currentLevel") val currentLevel: String? = null,
    @Json(name = "nextLevel") val nextLevel: String? = null,
    @Json(name = "currentAuthenticationMethods") val currentAuthenticationMethods: List<Map<String, Any>>? = null
)

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val isAal2Verified: Boolean = false
)

// ==================== PROFILES & ROLES ====================

@JsonClass(generateAdapter = true)
data class ProfileDto(
    @Json(name = "id") val id: String,
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "bar_number") val barNumber: String? = null,
    @Json(name = "status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class UserRoleDto(
    @Json(name = "role_id") val roleId: String,
    @Json(name = "roles") val roles: RoleLabelDto? = null
)

@JsonClass(generateAdapter = true)
data class RoleLabelDto(
    @Json(name = "label_en") val labelEn: String? = null
)

// ==================== MATTERS ====================

@JsonClass(generateAdapter = true)
data class MatterDto(
    @Json(name = "id") val id: String,
    @Json(name = "matter_label") val matterLabel: String,
    @Json(name = "court") val court: String? = null,
    @Json(name = "circuit") val circuit: String? = null,
    @Json(name = "case_number") val caseNumber: String? = null,
    @Json(name = "case_year") val caseYear: Int? = null,
    @Json(name = "matter_type") val matterType: String? = null,
    @Json(name = "stage") val stage: String? = null,
    @Json(name = "status") val status: String = "active",
    @Json(name = "subject") val subject: String? = null,
    @Json(name = "opened_at") val openedAt: String? = null
)

// ==================== PARTIES ====================

@JsonClass(generateAdapter = true)
data class PartyDto(
    @Json(name = "id") val id: String,
    @Json(name = "matter_id") val matterId: String,
    @Json(name = "party_role") val partyRole: String,
    @Json(name = "name") val name: String,
    @Json(name = "identifier") val identifier: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

// ==================== HEARINGS ====================

@JsonClass(generateAdapter = true)
data class HearingDto(
    @Json(name = "id") val id: String,
    @Json(name = "matter_id") val matterId: String,
    @Json(name = "session_date") val sessionDate: String,
    @Json(name = "session_time") val sessionTime: String? = null,
    @Json(name = "outcome") val outcome: String? = null,
    @Json(name = "adjournment_reason") val adjournmentReason: String? = null,
    @Json(name = "next_session_date") val nextSessionDate: String? = null,
    @Json(name = "matters") val matters: MatterDto? = null
)

// ==================== DEADLINES ====================

@JsonClass(generateAdapter = true)
data class DeadlineDto(
    @Json(name = "id") val id: String,
    @Json(name = "matter_id") val matterId: String,
    @Json(name = "rule_id") val ruleId: String? = null,
    @Json(name = "trigger_event") val triggerEvent: String,
    @Json(name = "trigger_date") val triggerDate: String? = null,
    @Json(name = "computed_due_date") val computedDueDate: String,
    @Json(name = "status") val status: String = "provisional",
    @Json(name = "matters") val matters: MatterDto? = null
)

@JsonClass(generateAdapter = true)
data class DeadlineRuleDto(
    @Json(name = "id") val id: String,
    @Json(name = "rule_key") val ruleKey: String,
    @Json(name = "title_ar") val titleAr: String? = null,
    @Json(name = "title_en") val titleEn: String? = null,
    @Json(name = "trigger_event") val triggerEvent: String? = null,
    @Json(name = "duration_value") val durationValue: Int? = null,
    @Json(name = "duration_unit") val durationUnit: String? = null,
    @Json(name = "citation") val citation: String? = null,
    @Json(name = "status") val status: String? = null
)

// ==================== DOCUMENTS & EXTRACTIONS ====================

@JsonClass(generateAdapter = true)
data class DocumentDto(
    @Json(name = "id") val id: String,
    @Json(name = "matter_id") val matterId: String,
    @Json(name = "original_filename") val originalFilename: String? = null,
    @Json(name = "ocr_status") val ocrStatus: String = "pending",
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "bucket_id") val bucketId: String? = null,
    @Json(name = "storage_path") val storagePath: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class DocumentPageDto(
    @Json(name = "page_number") val pageNumber: Int,
    @Json(name = "text_content") val textContent: String? = null
)

@JsonClass(generateAdapter = true)
data class IngestResponse(
    @Json(name = "document_id") val documentId: String,
    @Json(name = "job_id") val jobId: String? = null,
    @Json(name = "storage_path") val storagePath: String,
    @Json(name = "upload_url") val uploadUrl: String,
    @Json(name = "token") val token: String? = null
)

@JsonClass(generateAdapter = true)
data class ExtractionDto(
    @Json(name = "id") val id: String,
    @Json(name = "matter_id") val matterId: String,
    @Json(name = "document_id") val documentId: String,
    @Json(name = "field_key") val fieldKey: String,
    @Json(name = "field_value") val fieldValue: String? = null,
    @Json(name = "confidence") val confidence: Double? = null,
    @Json(name = "review_status") val reviewStatus: String = "pending",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "matters") val matters: MatterDto? = null
)

// ==================== DRAFTS & CITATIONS ====================

@JsonClass(generateAdapter = true)
data class DraftDto(
    @Json(name = "id") val id: String,
    @Json(name = "matter_id") val matterId: String,
    @Json(name = "doc_type") val docType: String,
    @Json(name = "version") val version: Int = 1,
    @Json(name = "status") val status: String = "drafting",
    @Json(name = "content_text") val contentText: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class DraftCitationDto(
    @Json(name = "id") val id: String,
    @Json(name = "draft_id") val draftId: String,
    @Json(name = "citation_text") val citationText: String,
    @Json(name = "authority_chunk_id") val authorityChunkId: String? = null,
    @Json(name = "status") val status: String = "unverified",
    @Json(name = "authority_chunks") val authorityChunks: AuthorityChunkWithAuthorityDto? = null
)

@JsonClass(generateAdapter = true)
data class AuthorityChunkWithAuthorityDto(
    @Json(name = "chunk_text") val chunkText: String? = null,
    @Json(name = "chunk_ref") val chunkRef: String? = null,
    @Json(name = "authorities") val authorities: AuthoritySimpleDto? = null
)

@JsonClass(generateAdapter = true)
data class AuthoritySimpleDto(
    @Json(name = "title") val title: String? = null,
    @Json(name = "citation") val citation: String? = null,
    @Json(name = "verification_status") val verificationStatus: String? = null,
    @Json(name = "repealed_date") val repealedDate: String? = null,
    @Json(name = "madhhab") val madhhab: String? = null
)

// ==================== CHRONOLOGY ====================

@JsonClass(generateAdapter = true)
data class ChronologyResponse(
    @Json(name = "events") val events: List<ChronologyEventDto> = emptyList(),
    @Json(name = "truncated") val truncated: Boolean = false,
    @Json(name = "pages_used") val pagesUsed: Int? = null
)

@JsonClass(generateAdapter = true)
data class ChronologyEventDto(
    @Json(name = "date") val date: String? = null,
    @Json(name = "description") val description: String,
    @Json(name = "source_document_id") val sourceDocumentId: String,
    @Json(name = "source_page_number") val sourcePageNumber: Int
)

// ==================== CASE ANALYSIS ====================

@JsonClass(generateAdapter = true)
data class CaseAnalysisResponse(
    @Json(name = "analysis") val analysis: String? = null,
    @Json(name = "case_context") val caseContext: CaseContextDto? = null,
    @Json(name = "matches") val matches: List<AuthorityMatchDto> = emptyList(),
    @Json(name = "web_sources") val webSources: List<WebSourceDto> = emptyList(),
    @Json(name = "web_search_queries") val webSearchQueries: List<String> = emptyList(),
    @Json(name = "warnings") val warnings: List<String> = emptyList(),
    @Json(name = "retrieval") val retrieval: String? = null,
    @Json(name = "note") val note: String? = null,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class CaseContextDto(
    @Json(name = "pages_used") val pagesUsed: Int = 0,
    @Json(name = "documents_used") val documentsUsed: Int = 0,
    @Json(name = "truncated") val truncated: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AuthorityMatchDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String,
    @Json(name = "citation") val citation: String,
    @Json(name = "chunk_ref") val chunkRef: String? = null,
    @Json(name = "authority_type") val authorityType: String? = null,
    @Json(name = "madhhab") val madhhab: String? = null,
    @Json(name = "verification_status") val verificationStatus: String? = null,
    @Json(name = "score") val score: Double? = null
)

@JsonClass(generateAdapter = true)
data class WebSourceDto(
    @Json(name = "uri") val uri: String? = null,
    @Json(name = "title") val title: String? = null
)

data class CachedCaseAnalysis(
    val result: CaseAnalysisResponse,
    val generatedAtEpochMs: Long
)

// ==================== RESEARCH ====================

@JsonClass(generateAdapter = true)
data class ResearchResponse(
    @Json(name = "answer") val answer: String? = null,
    @Json(name = "matches") val matches: List<AuthorityMatchDto> = emptyList(),
    @Json(name = "as_of") val asOf: String? = null,
    @Json(name = "retrieval") val retrieval: String? = null,
    @Json(name = "warnings") val warnings: List<String> = emptyList(),
    @Json(name = "note") val note: String? = null,
    @Json(name = "error") val error: String? = null
)

// ==================== ARCHIVE SEARCH ====================

@JsonClass(generateAdapter = true)
data class ArchiveSearchResponse(
    @Json(name = "results") val results: List<ArchiveSearchResultDto> = emptyList(),
    @Json(name = "note") val note: String? = null
)

@JsonClass(generateAdapter = true)
data class ArchiveSearchResultDto(
    @Json(name = "chunk_id") val chunkId: String,
    @Json(name = "document_id") val documentId: String? = null,
    @Json(name = "document_name") val documentName: String? = null,
    @Json(name = "matter_id") val matterId: String? = null,
    @Json(name = "matter_label") val matterLabel: String? = null,
    @Json(name = "court") val court: String? = null,
    @Json(name = "circuit") val circuit: String? = null,
    @Json(name = "case_number") val caseNumber: String? = null,
    @Json(name = "case_year") val caseYear: Int? = null,
    @Json(name = "page_number") val pageNumber: Int? = null,
    @Json(name = "excerpt") val excerpt: String? = null
)

// ==================== TEMPLATES ====================

@JsonClass(generateAdapter = true)
data class TemplateDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "doc_type") val docType: String,
    @Json(name = "content_text") val contentText: String? = null,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "created_at") val createdAt: String? = null
)

// ==================== AUTHORITIES ====================

@JsonClass(generateAdapter = true)
data class AuthorityDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "authority_type") val authorityType: String,
    @Json(name = "citation") val citation: String,
    @Json(name = "effective_date") val effectiveDate: String? = null,
    @Json(name = "repealed_date") val repealedDate: String? = null,
    @Json(name = "verification_status") val verificationStatus: String = "machine_ingested",
    @Json(name = "madhhab") val madhhab: String? = null,
    @Json(name = "added_by") val addedBy: String? = null,
    @Json(name = "authority_chunks") val authorityChunks: List<Map<String, Any>>? = null
)

// ==================== HEARING BRIEFING ====================
// Real function: litigation-hearing-briefing. Persisted server-side as a
// drafts row (doc_type='hearing_briefing') — same reason every other AI
// output in this build is — so it shows up in the matter's own Drafts
// tab automatically once generated; nothing extra to store client-side.

@JsonClass(generateAdapter = true)
data class HearingBriefingResponse(
    @Json(name = "draft_id") val draftId: String? = null,
    @Json(name = "version") val version: Int? = null,
    @Json(name = "content_text") val contentText: String? = null,
    @Json(name = "relevant_authorities") val relevantAuthorities: List<Map<String, Any>> = emptyList(),
    @Json(name = "truncated_context") val truncatedContext: Boolean = false,
    @Json(name = "error") val error: String? = null
)

// ==================== SUMMARIZE (document or whole case) ====================
// Real function: litigation-summarize. Ephemeral — never persisted
// server-side, this is a read aid, not a work product — so it's held
// only in-memory client-side and lost on navigation, same as chronology.

@JsonClass(generateAdapter = true)
data class SummarizeResponse(
    @Json(name = "scope") val scope: String? = null, // "document" | "case"
    @Json(name = "document_id") val documentId: String? = null,
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "key_points") val keyPoints: List<String> = emptyList(),
    @Json(name = "flags") val flags: List<String> = emptyList(),
    @Json(name = "truncated_context") val truncatedContext: Boolean = false,
    @Json(name = "note") val note: String? = null,
    @Json(name = "error") val error: String? = null
)

// ==================== CONFLICT CHECK ====================
// Real function: litigation-check-conflicts. Deliberately searches party
// names firm-wide, across matters the caller can't otherwise see — that
// is the whole point of a conflict check. AI triage (ai_notes,
// overall_recommendation) is advisory only, over the real matches;
// matches themselves come from fn_check_conflicts regardless of whether
// the AI step succeeds.

@JsonClass(generateAdapter = true)
data class ConflictMatchDto(
    @Json(name = "matter_id") val matterId: String,
    @Json(name = "matter_label") val matterLabel: String,
    @Json(name = "party_role") val partyRole: String,
    @Json(name = "matched_name") val matchedName: String,
    @Json(name = "identifier") val identifier: String? = null,
    @Json(name = "score") val score: Double
)

@JsonClass(generateAdapter = true)
data class ConflictCheckResponse(
    @Json(name = "matches") val matches: List<ConflictMatchDto> = emptyList(),
    @Json(name = "overall_recommendation") val overallRecommendation: String? = null, // no_conflict_evident | needs_review | likely_conflict
    @Json(name = "ai_notes") val aiNotes: List<String>? = null,
    @Json(name = "ai_error") val aiError: String? = null,
    @Json(name = "error") val error: String? = null
)

// ==================== DASHBOARD STATS ====================

data class DashboardStatsData(
    val activeMattersCount: Int = 0,
    val todayHearingsCount: Int = 0,
    val weekHearingsCount: Int = 0,
    val pendingDeadlinesCount: Int = 0,
    val overdueDeadlinesCount: Int = 0,
    val pendingExtractionsCount: Int = 0,
    val activeTemplatesCount: Int = 0,
    val todayHearings: List<HearingDto> = emptyList(),
    val topProvisionalDeadlines: List<DeadlineDto> = emptyList(),
    val urgentAlerts: List<UrgentAlert> = emptyList()
)

// ==================== URGENT ALERTS ====================

data class UrgentAlert(
    val id: String,
    val type: AlertType,
    val matterId: String,
    val matterLabel: String,
    val title: String,
    val date: String,
    val timeOrDetail: String?,
    val daysUntil: Int,
    val isProvisionalDeadline: Boolean = false
)

enum class AlertType {
    HEARING,
    DEADLINE
}
