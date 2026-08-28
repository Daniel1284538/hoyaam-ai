package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.data.api.SupabaseClient
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class RequiresMfaEnroll(val factor: FactorDto) : AuthState()
    data class RequiresMfaChallenge(val factorId: String, val challengeId: String) : AuthState()
    data class Authenticated(val session: UserSession) : AuthState()
}

class LitigationRepository(
    private val context: Context,
    private val client: SupabaseClient = SupabaseClient()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hoyaam_session", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _matters = MutableStateFlow<List<MatterDto>>(emptyList())
    val matters: StateFlow<List<MatterDto>> = _matters.asStateFlow()

    private val _urgentAlerts = MutableStateFlow<List<UrgentAlert>>(emptyList())
    val urgentAlerts: StateFlow<List<UrgentAlert>> = _urgentAlerts.asStateFlow()

    private val _authorities = MutableStateFlow<List<AuthorityDto>>(emptyList())
    val authorities: StateFlow<List<AuthorityDto>> = _authorities.asStateFlow()

    private val _templates = MutableStateFlow<List<TemplateDto>>(emptyList())
    val templates: StateFlow<List<TemplateDto>> = _templates.asStateFlow()

    private val _pendingExtractions = MutableStateFlow<List<ExtractionDto>>(emptyList())
    val pendingExtractions: StateFlow<List<ExtractionDto>> = _pendingExtractions.asStateFlow()

    private val _cachedCaseAnalysis = MutableStateFlow<CachedCaseAnalysis?>(null)
    val cachedCaseAnalysis: StateFlow<CachedCaseAnalysis?> = _cachedCaseAnalysis.asStateFlow()

    // In-memory cache for AI case analysis per matter_id
    private val caseAnalysisCache = ConcurrentHashMap<String, CachedCaseAnalysis>()

    // Per-draft (not per-matter — a matter can have several memos) cache
    // for a legal memo's supplementary web research. A full map, not a
    // single value, since more than one memo card can be visible on the
    // Drafts tab at once.
    private val _memoWebResearch = MutableStateFlow<Map<String, CachedMemoWebResearch>>(emptyMap())
    val memoWebResearch: StateFlow<Map<String, CachedMemoWebResearch>> = _memoWebResearch.asStateFlow()

    // Current active session
    var currentSession: UserSession? = null
        private set

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val accessToken = prefs.getString("access_token", null)
        val refreshToken = prefs.getString("refresh_token", null)
        val userId = prefs.getString("user_id", null)
        val email = prefs.getString("email", null)
        val isAal2 = prefs.getBoolean("is_aal2", false)

        if (!accessToken.isNullOrEmpty() && !userId.isNullOrEmpty() && isAal2) {
            val session = UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken.orEmpty(),
                userId = userId,
                email = email.orEmpty(),
                isAal2Verified = true
            )
            currentSession = session
            _authState.value = AuthState.Authenticated(session)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    private fun saveSession(session: UserSession) {
        currentSession = session
        prefs.edit()
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putString("user_id", session.userId)
            .putString("email", session.email)
            .putBoolean("is_aal2", session.isAal2Verified)
            .apply()
    }

    fun signOut() {
        clearSession()
    }

    fun clearSession() {
        currentSession = null
        caseAnalysisCache.clear()
        prefs.edit().clear().apply()
        _authState.value = AuthState.Unauthenticated
    }

    // ==================== AUTH & MFA FLOWS ====================

    suspend fun signInWithPassword(email: String, password: String): Result<AuthState> {
        return try {
            val state = signIn(email, password)
            Result.success(state)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): AuthState {
        val authResp = client.signInWithPassword(email.trim(), password)
        val token = authResp.accessToken ?: throw IllegalStateException("Access token missing in response")
        val userId = authResp.user?.id ?: throw IllegalStateException("User ID missing in response")
        val userEmail = authResp.user.email ?: email

        // This is only an aal1 token (password verified, MFA not yet) — hold
        // it in memory so completeMfaChallenge() below has something to call
        // back with. Not persisted to prefs and isAal2Verified stays false,
        // so a kill mid-MFA doesn't leave a stale "logged in" session behind;
        // saveSession() replaces this with the real aal2 session once the
        // challenge is verified.
        currentSession = UserSession(
            accessToken = token,
            refreshToken = authResp.refreshToken.orEmpty(),
            userId = userId,
            email = userEmail,
            isAal2Verified = false
        )

        // Mandatory MFA check
        val factorList = client.listFactors(token)
        val verifiedFactor = factorList.totp?.firstOrNull { it.status == "verified" }
            ?: factorList.all?.firstOrNull { it.factorType == "totp" && it.status == "verified" }

        if (verifiedFactor == null) {
            // Unenroll any stale unverified factor first
            val unverified = factorList.totp?.firstOrNull { it.status == "unverified" }
                ?: factorList.all?.firstOrNull { it.status == "unverified" }
            if (unverified != null) {
                try {
                    client.unenrollFactor(token, unverified.id)
                } catch (_: Exception) {}
            }

            // Enroll a new TOTP factor
            val newFactor = client.enrollTotp(token)
            val state = AuthState.RequiresMfaEnroll(newFactor)
            _authState.value = state
            return state
        }

        // Verified factor exists -> create challenge
        val challenge = client.challengeFactor(token, verifiedFactor.id)
        val state = AuthState.RequiresMfaChallenge(
            factorId = verifiedFactor.id,
            challengeId = challenge.id
        )
        _authState.value = state
        return state
    }

    suspend fun challengeAndVerifyMfa(factorId: String, code: String): Result<UserSession> {
        return try {
            val challenge = client.challengeFactor(requireToken(), factorId)
            val session = completeMfaChallenge(factorId, challenge.id, code)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyChallenge(factorId: String, challengeId: String, code: String): Result<UserSession> {
        return try {
            val session = completeMfaChallenge(factorId, challengeId, code)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeMfaChallenge(
        factorId: String,
        challengeId: String,
        code: String
    ): UserSession {
        val token = currentSession?.accessToken
            ?: prefs.getString("access_token", null)
            ?: throw IllegalStateException("No active token to verify MFA")

        val authResp = client.verifyChallenge(token, factorId, challengeId, code.trim())
        val newToken = authResp.accessToken ?: token
        val newRefresh = authResp.refreshToken ?: currentSession?.refreshToken.orEmpty()
        val userId = authResp.user?.id ?: currentSession?.userId ?: ""
        val email = authResp.user?.email ?: currentSession?.email ?: ""

        val session = UserSession(
            accessToken = newToken,
            refreshToken = newRefresh,
            userId = userId,
            email = email,
            isAal2Verified = true
        )

        saveSession(session)
        _authState.value = AuthState.Authenticated(session)
        refreshAll()
        return session
    }

    // Access tokens are short-lived (commonly ~1 hour). The refresh_token
    // was previously stored and never used — every call just kept using
    // the stale access token until it started 401ing, with no recovery.
    // This decodes the JWT's own `exp` claim (no network call) and
    // proactively refreshes whenever the token is within 60s of expiring
    // or already expired, before the caller's real request goes out.
    private suspend fun requireToken(): String {
        val token = currentSession?.accessToken
            ?: prefs.getString("access_token", null)
            ?: throw IllegalStateException("Not authenticated")

        val expiry = decodeJwtExpiry(token)
        val nowSec = System.currentTimeMillis() / 1000
        if (expiry == null || expiry - nowSec < 60) {
            tryRefreshToken()?.let { return it }
        }
        return currentSession?.accessToken ?: token
    }

    private fun decodeJwtExpiry(jwt: String): Long? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            JSONObject(payload).optLong("exp", 0L).takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun tryRefreshToken(): String? {
        val refreshToken = currentSession?.refreshToken
            ?: prefs.getString("refresh_token", null)
            ?: return null
        if (refreshToken.isBlank()) return null

        return try {
            val authResp = client.refreshSession(refreshToken)
            val newToken = authResp.accessToken ?: return null
            val newRefresh = authResp.refreshToken ?: refreshToken
            val updated = UserSession(
                accessToken = newToken,
                refreshToken = newRefresh,
                userId = authResp.user?.id ?: currentSession?.userId ?: prefs.getString("user_id", "").orEmpty(),
                email = authResp.user?.email ?: currentSession?.email ?: prefs.getString("email", "").orEmpty(),
                isAal2Verified = true
            )
            saveSession(updated)
            newToken
        } catch (e: Exception) {
            // Refresh token itself is dead (expired/revoked) — the next
            // real API call will 401 and the caller's own error handling
            // surfaces that; not forcing a sign-out from inside a
            // background token check.
            null
        }
    }

    suspend fun refreshAll() {
        try {
            _matters.value = getMatters()
            _urgentAlerts.value = getUrgentAlerts()
            _authorities.value = getAuthorities()
            _templates.value = getTemplates()
            _pendingExtractions.value = getPendingExtractions()
        } catch (e: Exception) {
            // Keep current values
        }
    }

    // ==================== DASHBOARD & STATS ====================

    suspend fun getDashboardStats(): DashboardStatsData {
        val token = requireToken()

        // Run this first, not last: on a restored session (app relaunch with
        // an already-persisted aal2 session), this call chain is the only
        // thing that ever populates matters/urgentAlerts/authorities/
        // templates/pendingExtractions. It used to sit at the end of this
        // function — if any one of the dashboard-specific queries below
        // threw, refreshAll() never ran and every one of those lists stayed
        // empty forever, with the exception silently swallowed by this
        // function's caller. Doing it first means a dashboard-stats failure
        // can no longer block the rest of the app's data from loading.
        refreshAll()

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
        val nextWeek = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

        val todayHearings = client.queryRestList(
            "hearings?session_date=eq.$today&select=id,matter_id,session_date,session_time,adjournment_reason,matters(matter_label,court,circuit,case_number,case_year)&order=session_time.asc",
            token,
            HearingDto::class.java
        )

        val weekHearings = client.queryRestList(
            "hearings?session_date=gte.$today&session_date=lte.$nextWeek&select=id",
            token,
            IdOnlyDto::class.java
        )

        val overdueDeadlines = client.queryRestList(
            "deadlines?status=in.(provisional,confirmed)&computed_due_date=lt.$today&select=id",
            token,
            IdOnlyDto::class.java
        )

        val pendingExtractions = client.queryRestList(
            "extractions?review_status=eq.pending&select=id",
            token,
            IdOnlyDto::class.java
        )

        val activeTemplates = client.queryRestList(
            "templates?active=eq.true&select=id",
            token,
            IdOnlyDto::class.java
        )

        val topProvisionalDeadlines = client.queryRestList(
            "deadlines?status=eq.provisional&select=id,matter_id,trigger_event,computed_due_date,matters(matter_label,case_number,case_year)&order=computed_due_date.asc&limit=5",
            token,
            DeadlineDto::class.java
        )

        return DashboardStatsData(
            todayHearingsCount = todayHearings.size,
            todayHearings = todayHearings,
            weekHearingsCount = weekHearings.size,
            overdueDeadlinesCount = overdueDeadlines.size,
            pendingExtractionsCount = pendingExtractions.size,
            activeTemplatesCount = activeTemplates.size,
            topProvisionalDeadlines = topProvisionalDeadlines
        )
    }

    suspend fun getUrgentAlerts(): List<UrgentAlert> {
        val token = requireToken()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }
        val horizon = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayDate = sdf.parse(today) ?: Date()

        val hearings = client.queryRestList(
            "hearings?session_date=gte.$today&session_date=lte.$horizon&select=id,matter_id,session_date,session_time,adjournment_reason,matters(matter_label,court,circuit,case_number,case_year)&order=session_date.asc",
            token,
            HearingDto::class.java
        )

        val deadlines = client.queryRestList(
            "deadlines?status=in.(provisional,confirmed)&computed_due_date=lte.$horizon&select=id,matter_id,trigger_event,computed_due_date,status,matters(matter_label,case_number,case_year)&order=computed_due_date.asc",
            token,
            DeadlineDto::class.java
        )

        val alerts = mutableListOf<UrgentAlert>()

        for (h in hearings) {
            val hDate = try { sdf.parse(h.sessionDate) } catch (e: Exception) { null }
            val days = if (hDate != null) ((hDate.time - todayDate.time) / (1000 * 60 * 60 * 24)).toInt() else 0
            val courtInfo = listOfNotNull(h.matters?.court, h.matters?.circuit?.let { "($it)" }, h.sessionTime?.take(5)).joinToString(" ")
            alerts.add(
                UrgentAlert(
                    id = h.id,
                    type = AlertType.HEARING,
                    matterId = h.matterId,
                    matterLabel = h.matters?.matterLabel ?: "جلسة",
                    title = "جلسة: ${h.matters?.matterLabel ?: ""}",
                    date = h.sessionDate,
                    timeOrDetail = courtInfo,
                    daysUntil = days
                )
            )
        }

        for (d in deadlines) {
            val dDate = try { sdf.parse(d.computedDueDate) } catch (e: Exception) { null }
            val days = if (dDate != null) ((dDate.time - todayDate.time) / (1000 * 60 * 60 * 24)).toInt() else 0
            alerts.add(
                UrgentAlert(
                    id = d.id,
                    type = AlertType.DEADLINE,
                    matterId = d.matterId,
                    matterLabel = d.matters?.matterLabel ?: "موعد",
                    title = d.triggerEvent,
                    date = d.computedDueDate,
                    timeOrDetail = d.matters?.matterLabel,
                    daysUntil = days,
                    isProvisionalDeadline = d.status == "provisional"
                )
            )
        }

        return alerts.sortedBy { it.daysUntil }
    }

    // ==================== MATTERS ====================

    suspend fun getMatters(): List<MatterDto> {
        val token = requireToken()
        return client.queryRestList(
            "matters?select=id,matter_label,court,circuit,case_number,case_year,matter_type,stage,status,subject,opened_at&order=opened_at.desc",
            token,
            MatterDto::class.java
        )
    }

    suspend fun getMatter(matterId: String): MatterDto? {
        val token = requireToken()
        return client.queryRestSingle(
            "matters?id=eq.$matterId&select=id,matter_label,court,circuit,case_number,case_year,matter_type,stage,status,subject,opened_at",
            token,
            MatterDto::class.java
        )
    }

    suspend fun createMatter(matter: MatterDto): String {
        return createMatter(
            matterLabel = matter.matterLabel,
            court = matter.court,
            circuit = matter.circuit,
            caseNumber = matter.caseNumber,
            caseYear = matter.caseYear,
            matterType = matter.matterType,
            stage = matter.stage,
            subject = matter.subject
        )
    }

    suspend fun createMatter(
        matterLabel: String,
        court: String?,
        circuit: String?,
        caseNumber: String?,
        caseYear: Int?,
        matterType: String?,
        stage: String?,
        subject: String?,
        subjectPendingDocuments: Boolean = false
    ): String {
        val token = requireToken()
        // Real function: litigation-create-matter. Every one of these
        // fields is required server-side except parties (see that
        // function's own header comment) — matter_label/court/circuit/
        // case_number/case_year/matter_type/stage/subject must all be
        // real, non-empty values, or the call is rejected with a specific
        // "<field> is required" error. No {action: "create"} wrapper —
        // the fields go directly in the body. subject_pending_documents
        // is the one escape hatch — skips the subject-required check
        // server-side, matching the web app's "upload documents instead"
        // toggle, so a lawyer can create the matter and attach scans
        // right away instead of typing a subject by hand up front.
        val json = JSONObject().apply {
            put("matter_label", matterLabel)
            put("court", court.orEmpty())
            put("circuit", circuit.orEmpty())
            put("case_number", caseNumber.orEmpty())
            put("case_year", caseYear ?: JSONObject.NULL)
            put("matter_type", matterType.orEmpty())
            put("stage", stage.orEmpty())
            put("subject", subject.orEmpty())
            if (subjectPendingDocuments) put("subject_pending_documents", true)
        }.toString()

        val response = client.callEdgeFunction("litigation-create-matter", json, token)
        refreshAll()
        return JSONObject(response).getString("matter_id")
    }

    suspend fun updateMatter(matter: MatterDto) {
        val fields = mutableMapOf<String, Any?>()
        fields["matter_label"] = matter.matterLabel
        fields["court"] = matter.court
        fields["circuit"] = matter.circuit
        fields["case_number"] = matter.caseNumber
        fields["case_year"] = matter.caseYear
        fields["matter_type"] = matter.matterType
        fields["stage"] = matter.stage
        fields["status"] = matter.status
        fields["subject"] = matter.subject
        updateMatter(matter.id, fields)
    }

    suspend fun updateMatter(matterId: String, fields: Map<String, Any?>) {
        val token = requireToken()
        // Real function: litigation-update-matter. No {action: "update"}
        // wrapper. Only keys actually present in the body are touched —
        // a present key set to null explicitly clears that field
        // server-side, so JSONObject.NULL is written deliberately here,
        // not skipped, when the caller passed null.
        val json = JSONObject().apply {
            put("matter_id", matterId)
            for ((k, v) in fields) {
                put(k, v ?: JSONObject.NULL)
            }
        }.toString()

        client.callEdgeFunction("litigation-update-matter", json, token)
        refreshAll()
    }

    // ==================== PARTIES ====================

    suspend fun getParties(matterId: String): List<PartyDto> {
        val token = requireToken()
        return client.queryRestList(
            "matter_parties?matter_id=eq.$matterId&select=id,matter_id,party_role,name,identifier,notes,created_at&order=created_at.asc",
            token,
            PartyDto::class.java
        )
    }

    suspend fun addParty(party: PartyDto): String {
        return addParty(
            matterId = party.matterId,
            partyRole = party.partyRole,
            name = party.name,
            identifier = party.identifier,
            notes = party.notes
        )
    }

    suspend fun addParty(
        matterId: String,
        partyRole: String,
        name: String,
        identifier: String?,
        notes: String?
    ): String {
        val token = requireToken()
        val json = JSONObject().apply {
            put("action", "add")
            put("matter_id", matterId)
            put("party_role", partyRole)
            put("name", name)
            if (!identifier.isNullOrEmpty()) put("identifier", identifier)
            if (!notes.isNullOrEmpty()) put("notes", notes)
        }.toString()

        val response = client.callEdgeFunction("litigation-manage-parties", json, token)
        return JSONObject(response).getString("party_id")
    }

    suspend fun updateParty(party: PartyDto) {
        updateParty(
            partyId = party.id,
            partyRole = party.partyRole,
            name = party.name,
            identifier = party.identifier,
            notes = party.notes
        )
    }

    suspend fun updateParty(
        partyId: String,
        partyRole: String?,
        name: String?,
        identifier: String?,
        notes: String?
    ) {
        val token = requireToken()
        val json = JSONObject().apply {
            put("action", "update")
            put("party_id", partyId)
            if (!partyRole.isNullOrEmpty()) put("party_role", partyRole)
            if (!name.isNullOrEmpty()) put("name", name)
            if (!identifier.isNullOrEmpty()) put("identifier", identifier)
            if (!notes.isNullOrEmpty()) put("notes", notes)
        }.toString()

        client.callEdgeFunction("litigation-manage-parties", json, token)
    }

    suspend fun deleteParty(partyId: String) {
        val token = requireToken()
        val json = JSONObject().apply {
            put("action", "delete")
            put("party_id", partyId)
        }.toString()

        client.callEdgeFunction("litigation-manage-parties", json, token)
    }

    // ==================== HEARINGS & ROLL ====================

    suspend fun getHearings(matterId: String): List<HearingDto> {
        val token = requireToken()
        return client.queryRestList(
            "hearings?matter_id=eq.$matterId&select=id,matter_id,session_date,session_time,outcome,adjournment_reason,next_session_date&order=session_date.desc",
            token,
            HearingDto::class.java
        )
    }

    suspend fun getHearingsForDate(date: String): List<HearingDto> {
        val token = requireToken()
        return client.queryRestList(
            "hearings?session_date=eq.$date&select=id,matter_id,session_date,session_time,outcome,adjournment_reason,next_session_date,matters(matter_label,court,circuit,case_number,case_year)&order=session_time.asc",
            token,
            HearingDto::class.java
        )
    }

    suspend fun getMonthHearingDates(monthPrefix: String): Set<String> {
        val token = requireToken()
        val list = client.queryRestList(
            "hearings?session_date=gte.$monthPrefix-01&session_date=lte.$monthPrefix-31&select=session_date",
            token,
            HearingDto::class.java
        )
        return list.map { it.sessionDate }.toSet()
    }

    suspend fun recordHearing(hearing: HearingDto): String {
        return recordHearing(
            matterId = hearing.matterId,
            sessionDate = hearing.sessionDate,
            sessionTime = hearing.sessionTime,
            outcome = hearing.outcome,
            adjournmentReason = hearing.adjournmentReason,
            nextSessionDate = hearing.nextSessionDate
        )
    }

    suspend fun recordHearing(
        matterId: String,
        sessionDate: String,
        sessionTime: String?,
        outcome: String?,
        adjournmentReason: String?,
        nextSessionDate: String?
    ): String {
        val token = requireToken()
        val json = JSONObject().apply {
            put("matter_id", matterId)
            put("session_date", sessionDate)
            if (!sessionTime.isNullOrEmpty()) put("session_time", sessionTime)
            if (!outcome.isNullOrEmpty()) put("outcome", outcome)
            if (!adjournmentReason.isNullOrEmpty()) put("adjournment_reason", adjournmentReason)
            if (!nextSessionDate.isNullOrEmpty()) put("next_session_date", nextSessionDate)
        }.toString()

        val response = client.callEdgeFunction("litigation-record-hearing", json, token)
        return JSONObject(response).getString("hearing_id")
    }

    // ==================== DEADLINES ====================

    suspend fun getDeadlines(matterId: String): List<DeadlineDto> {
        val token = requireToken()
        return client.queryRestList(
            "deadlines?matter_id=eq.$matterId&select=id,matter_id,rule_id,trigger_event,trigger_date,computed_due_date,status&order=computed_due_date.asc",
            token,
            DeadlineDto::class.java
        )
    }

    // Real function: litigation-propose-deadline. It is THE only way a
    // deadlines row gets created, and it computes computed_due_date
    // itself, server-side, via fn_compute_deadline — deterministically,
    // from a real rule_id referencing an active row in deadline_rules.
    // It refuses to run against anything but an active, lawyer-approved
    // rule. This function used to compute the date client-side and never
    // sent a real rule_id at all — that bypassed the entire point of the
    // rule engine (no deadline before a lawyer has signed off on the rule
    // it's based on). Now it takes a real rule_id and lets the server do
    // the computation; the client never invents a due date.
    suspend fun getDeadlineRules(): List<DeadlineRuleDto> {
        val token = requireToken()
        return client.queryRestList(
            "deadline_rules?status=eq.active&select=id,rule_key,title_ar,title_en,trigger_event,duration_value,duration_unit,citation,status&order=title_ar.asc",
            token,
            DeadlineRuleDto::class.java
        )
    }

    suspend fun proposeDeadline(
        matterId: String,
        ruleId: String,
        triggerEvent: String,
        triggerDate: String
    ): String {
        val token = requireToken()
        val json = JSONObject().apply {
            put("matter_id", matterId)
            put("rule_id", ruleId)
            put("trigger_event", triggerEvent)
            put("trigger_date", triggerDate)
        }.toString()

        val response = client.callEdgeFunction("litigation-propose-deadline", json, token)
        return JSONObject(response).getString("deadline_id")
    }

    suspend fun confirmDeadline(deadlineId: String) {
        val token = requireToken()
        val json = JSONObject().apply {
            put("deadline_id", deadlineId)
        }.toString()

        client.callEdgeFunction("litigation-confirm-deadline", json, token)
    }

    // ==================== DOCUMENTS & UPLOAD ====================

    suspend fun getDocuments(matterId: String): List<DocumentDto> {
        val token = requireToken()
        return client.queryRestList(
            "documents?matter_id=eq.$matterId&select=id,matter_id,original_filename,ocr_status,mime_type,bucket_id,storage_path,created_at&order=created_at.desc",
            token,
            DocumentDto::class.java
        )
    }

    suspend fun uploadDocument(matterId: String, filename: String, docType: String, text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return ingestAndUploadFile(matterId, filename, "text/plain", bytes)
    }

    suspend fun ingestAndUploadFile(
        matterId: String,
        filename: String,
        mimeType: String,
        bytes: ByteArray
    ): String {
        val token = requireToken()
        val ingestJson = JSONObject().apply {
            put("matter_id", matterId)
            put("original_filename", filename)
            put("mime_type", mimeType)
        }.toString()

        val ingestResp = client.callEdgeFunction("legal-ingest", ingestJson, token)
        val ingestData = moshi.adapter(IngestResponse::class.java).fromJson(ingestResp)
            ?: throw IllegalStateException("Failed to parse ingest response")

        val uploadSuccess = client.uploadToSignedUrl(
            uploadUrl = ingestData.uploadUrl,
            token = ingestData.token,
            bytes = bytes,
            mimeType = mimeType
        )

        if (!uploadSuccess) {
            throw IOException("Failed to upload bytes to storage URL")
        }

        // Trigger extraction
        val extractJson = JSONObject().apply {
            put("document_id", ingestData.documentId)
            if (!ingestData.jobId.isNullOrEmpty()) put("job_id", ingestData.jobId)
        }.toString()

        client.callEdgeFunction("legal-extract", extractJson, token)
        return ingestData.documentId
    }

    // Real function: sign-document-url. There is no direct Storage RLS
    // grant to `authenticated` on any bucket, by design — this is the
    // ONLY path to an actual file. Previously "opening" a document was a
    // Toast with the filename and nothing else; this was never called.
    // Returns a short-lived (5 min) signed URL.
    suspend fun getDocumentSignedUrl(matterId: String, bucket: String, storagePath: String): String {
        val token = requireToken()
        val json = JSONObject().apply {
            put("matter_id", matterId)
            put("bucket", bucket)
            put("storage_path", storagePath)
        }.toString()

        val response = client.callEdgeFunction("sign-document-url", json, token)
        return JSONObject(response).getString("url")
    }

    // ==================== EXTRACTIONS (REVIEW QUEUE) ====================

    suspend fun getPendingExtractions(): List<ExtractionDto> {
        val token = requireToken()
        return client.queryRestList(
            "extractions?review_status=eq.pending&select=id,matter_id,document_id,field_key,field_value,confidence,review_status,created_at,matters(matter_label)&order=created_at.asc",
            token,
            ExtractionDto::class.java
        )
    }

    suspend fun reviewExtraction(
        extractionId: String,
        action: String, // "confirm", "correct", "reject"
        correctedValue: String? = null,
        notes: String? = null
    ) {
        val token = requireToken()
        val json = JSONObject().apply {
            put("extraction_id", extractionId)
            put("action", action)
            if (action == "correct" && !correctedValue.isNullOrEmpty()) {
                put("corrected_value", correctedValue)
            }
            if (!notes.isNullOrEmpty()) put("notes", notes)
        }.toString()

        client.callEdgeFunction("litigation-review-extraction", json, token)
        refreshAll()
    }

    // ==================== DRAFTS & CITATIONS ====================

    suspend fun getDrafts(matterId: String): List<DraftDto> {
        val token = requireToken()
        return client.queryRestList(
            "drafts?matter_id=eq.$matterId&select=id,matter_id,doc_type,version,status,content_text,created_at&order=created_at.desc",
            token,
            DraftDto::class.java
        )
    }

    suspend fun generateDraft(
        matterId: String,
        docType: String,
        instructions: String?,
        claims: List<String> = emptyList(),
        selectedAuthorityIds: List<String> = emptyList()
    ): String {
        val token = requireToken()
        val json = JSONObject().apply {
            put("matter_id", matterId)
            put("doc_type", docType)
            if (!instructions.isNullOrEmpty()) put("instructions", instructions)
            if (claims.isNotEmpty()) put("claims", JSONArray(claims))
            if (selectedAuthorityIds.isNotEmpty()) put("selected_authorities", JSONArray(selectedAuthorityIds))
        }.toString()

        val response = client.callEdgeFunction("litigation-draft", json, token)
        return JSONObject(response).optString("draft_id", UUID.randomUUID().toString())
    }

    // Real function: litigation-export-draft. Generates a real .docx (not
    // PDF — Arabic bidi/letter-shaping requires Word's own text engine)
    // and returns a short-lived signed URL to it. Previously this was a
    // Toast saying "Preparing Word file…" that never actually called
    // anything. Blocks server-side if the draft has unresolved citations
    // (see getDraftCitations/verifyCitation below) — surfacing that error
    // message directly is the point, not a bug to work around.
    suspend fun exportDraft(draftId: String): String {
        val token = requireToken()
        val json = JSONObject().apply { put("draft_id", draftId) }.toString()
        val response = client.callEdgeFunction("litigation-export-draft", json, token)
        return JSONObject(response).getString("url")
    }

    // ==================== DRAFT CITATIONS (real Citation Inspector data) ====================

    // Real table: draft_citations, bound server-side at draft-generation
    // time to an actual authority_chunk_id (or null for an unbound,
    // free-text citation — see litigation-verify-citation's own refusal
    // to "verify" one of those). Previously the Citation Inspector never
    // read this table at all — it ran a client-side regex over the raw
    // draft text and fuzzy-matched substrings against the locally loaded
    // authorities list, with no connection to the real verification data
    // or to litigation-verify-citation. That was presented as if it were
    // the real safety mechanism while actually being disconnected from
    // it entirely.
    suspend fun getDraftCitations(draftId: String): List<DraftCitationDto> {
        val token = requireToken()
        return client.queryRestList(
            "draft_citations?draft_id=eq.$draftId&select=id,draft_id,citation_text,authority_chunk_id,status,authority_chunks(chunk_text,chunk_ref,authorities(title,citation,verification_status,repealed_date,madhhab))",
            token,
            DraftCitationDto::class.java
        )
    }

    // Real function: litigation-verify-citation. The only path that
    // resolves a citation to 'verified' (refused server-side if
    // authority_chunk_id is null — nothing to check an unbound citation
    // against) or 'flagged'. This was never called anywhere before.
    suspend fun verifyCitation(citationId: String, status: String) {
        val token = requireToken()
        val json = JSONObject().apply {
            put("citation_id", citationId)
            put("status", status)
        }.toString()
        client.callEdgeFunction("litigation-verify-citation", json, token)
    }

    // ==================== ARCHIVE SEARCH (real firm-wide search) ====================

    // Real function: litigation-search-archive. Searches the firm's own
    // past filings server-side, across every matter the caller can
    // access — Arabic full-text search over document_chunks. Previously
    // never called; the Archive screen only searched this one device's
    // local Room cache, which is real and useful as a secondary,
    // explicitly-local search, but is not the firm's archive.
    suspend fun searchArchive(query: String, matterId: String? = null, court: String? = null): ArchiveSearchResponse {
        val token = requireToken()
        val json = JSONObject().apply {
            put("query", query)
            if (!matterId.isNullOrEmpty()) put("matter_id", matterId)
            if (!court.isNullOrEmpty()) put("court", court)
        }.toString()
        val response = client.callEdgeFunction("litigation-search-archive", json, token)
        return moshi.adapter(ArchiveSearchResponse::class.java).fromJson(response)
            ?: ArchiveSearchResponse(note = "تعذّر تحليل نتيجة البحث.")
    }

    suspend fun fillTemplate(matterId: String, templateId: String, overrides: Map<String, String>): String {
        val token = requireToken()
        val json = JSONObject().apply {
            put("matter_id", matterId)
            put("template_id", templateId)
            put("overrides", JSONObject(overrides))
        }.toString()

        val response = client.callEdgeFunction("litigation-fill-template", json, token)
        return JSONObject(response).optString("draft_id", UUID.randomUUID().toString())
    }

    // ==================== CHRONOLOGY ====================

    suspend fun generateChronology(matterId: String): ChronologyResponse {
        val token = requireToken()
        val json = JSONObject().apply {
            put("matter_id", matterId)
        }.toString()

        val response = client.callEdgeFunction("litigation-chronology", json, token)
        return moshi.adapter(ChronologyResponse::class.java).fromJson(response)
            ?: ChronologyResponse()
    }

    // ==================== AI CASE ANALYSIS ====================

    suspend fun analyzeCase(matterId: String): CaseAnalysisResponse {
        val token = requireToken()
        val json = JSONObject().apply {
            put("matter_id", matterId)
        }.toString()

        val response = client.callEdgeFunction("litigation-analyze-case", json, token)
        val analysis = moshi.adapter(CaseAnalysisResponse::class.java).fromJson(response)
            ?: CaseAnalysisResponse(error = "تعذّر معالجة رد التحليل")

        if (analysis.analysis != null || analysis.note != null) {
            val cached = CachedCaseAnalysis(analysis, System.currentTimeMillis())
            caseAnalysisCache[matterId] = cached
            _cachedCaseAnalysis.value = cached
        }
        return analysis
    }

    // caseAnalysisCache was write-only before this — analyzeCase() filled
    // it in, but nothing ever read it back, so _cachedCaseAnalysis (a
    // single non-keyed value) just kept whatever the last-analyzed matter
    // produced. Switching matters showed the wrong one's analysis, or none
    // at all, even for a matter already analyzed this session — the only
    // way to see it again was to hit "تحليل" and call Gemini again. Same
    // fix as the web app's per-matter caseAnalysisCache Map: look this
    // matter's entry up (or clear it) whenever the selected matter changes.
    fun syncCachedAnalysisFor(matterId: String) {
        _cachedCaseAnalysis.value = caseAnalysisCache[matterId]
    }

    // ==================== MEMO WEB RESEARCH ====================
    // Supplementary, non-citable web context for a legal memo — see
    // litigation-memo-web-research's own header for why this is a
    // deliberately separate call from litigation-memo itself, never
    // touching the memo's own content_text/draft_citations. Not
    // persisted server-side (the live web changes over time), so this
    // caches the result client-side per draft_id with a timestamp,
    // exactly like case analysis: reopening a matter shows what was
    // already generated, and there's a manual "regenerate" action for
    // when the lawyer wants a fresh pass.
    suspend fun generateMemoWebResearch(draftId: String): MemoWebResearchResponse {
        val token = requireToken()
        val json = JSONObject().apply {
            put("draft_id", draftId)
        }.toString()

        val response = client.callEdgeFunction("litigation-memo-web-research", json, token)
        val result = moshi.adapter(MemoWebResearchResponse::class.java).fromJson(response)
            ?: MemoWebResearchResponse(error = "تعذّر معالجة رد البحث")

        if (result.error == null) {
            val cached = CachedMemoWebResearch(result, System.currentTimeMillis())
            _memoWebResearch.value = _memoWebResearch.value + (draftId to cached)
        }
        return result
    }

    // ==================== HEARING BRIEFING ====================
    // Real function: litigation-hearing-briefing — never wired into the
    // app before. Persisted server-side as a drafts row, so no client
    // caching needed; refreshAll of the matter's drafts (loadMatter)
    // picks it up automatically after this call.
    suspend fun generateHearingBriefing(matterId: String, hearingId: String? = null): HearingBriefingResponse {
        val token = requireToken()
        val json = JSONObject().apply {
            put("matter_id", matterId)
            if (!hearingId.isNullOrEmpty()) put("hearing_id", hearingId)
        }.toString()
        val response = client.callEdgeFunction("litigation-hearing-briefing", json, token)
        return moshi.adapter(HearingBriefingResponse::class.java).fromJson(response)
            ?: HearingBriefingResponse(error = "تعذّر تحليل رد الإحاطة التحضيرية")
    }

    // ==================== SUMMARIZE ====================
    // Real function: litigation-summarize — never wired into the app
    // before. Ephemeral by design (never persisted server-side), so the
    // result is only held in ViewModel state, same as chronology.
    suspend fun summarize(matterId: String, documentId: String? = null): SummarizeResponse {
        val token = requireToken()
        val json = JSONObject().apply {
            put("matter_id", matterId)
            if (!documentId.isNullOrEmpty()) put("document_id", documentId)
        }.toString()
        val response = client.callEdgeFunction("litigation-summarize", json, token)
        return moshi.adapter(SummarizeResponse::class.java).fromJson(response)
            ?: SummarizeResponse(error = "تعذّر تحليل رد الملخص")
    }

    // ==================== CONFLICT CHECK ====================
    // Real function: litigation-check-conflicts — never wired into the
    // app before. Deliberately searches party names firm-wide, across
    // matters the caller can't otherwise see (that's the whole point of
    // a conflict check) — matter_id here is only "exclude this matter
    // from the search" (e.g. when checking a matter's own existing
    // parties against everything else), not a scope restriction.
    suspend fun checkConflicts(names: List<String>, excludeMatterId: String? = null): ConflictCheckResponse {
        val token = requireToken()
        val json = JSONObject().apply {
            put("names", JSONArray(names))
            if (!excludeMatterId.isNullOrEmpty()) put("matter_id", excludeMatterId)
        }.toString()
        val response = client.callEdgeFunction("litigation-check-conflicts", json, token)
        return moshi.adapter(ConflictCheckResponse::class.java).fromJson(response)
            ?: ConflictCheckResponse(error = "تعذّر تحليل رد فحص التعارض")
    }

    // ==================== RESEARCH ====================

    suspend fun performResearch(query: String): ResearchResponse {
        return runResearch(query = query)
    }

    suspend fun runResearch(
        query: String,
        asOf: String? = null,
        authorityTypes: List<String>? = null,
        verifiedOnly: Boolean = false
    ): ResearchResponse {
        val token = requireToken()
        val json = JSONObject().apply {
            put("query", query)
            if (!asOf.isNullOrEmpty()) put("as_of", asOf)
            if (!authorityTypes.isNullOrEmpty()) put("authority_types", JSONArray(authorityTypes))
            if (verifiedOnly) put("verified_only", true)
        }.toString()

        val response = client.callEdgeFunction("litigation-research", json, token)
        return moshi.adapter(ResearchResponse::class.java).fromJson(response)
            ?: ResearchResponse(note = "لا توجد نتائج.")
    }

    // ==================== TEMPLATES ====================

    suspend fun getTemplates(): List<TemplateDto> {
        val token = requireToken()
        return client.queryRestList(
            "templates?select=*&order=created_at.desc",
            token,
            TemplateDto::class.java
        )
    }

    suspend fun addTemplate(template: TemplateDto): String {
        return createTemplate(template.title, template.docType, template.contentText ?: "")
    }

    suspend fun createTemplate(title: String, docType: String, contentText: String): String {
        val token = requireToken()
        val json = JSONObject().apply {
            put("action", "create")
            put("title", title)
            put("doc_type", docType)
            put("content_text", contentText)
        }.toString()

        val response = client.callEdgeFunction("litigation-manage-template", json, token)
        refreshAll()
        return JSONObject(response).getString("template_id")
    }

    // ==================== LEGAL CORPUS ====================

    suspend fun getAuthorities(): List<AuthorityDto> {
        val token = requireToken()
        return client.queryRestList(
            "authorities?select=id,title,authority_type,citation,effective_date,repealed_date,verification_status,madhhab,added_by&order=created_at.desc",
            token,
            AuthorityDto::class.java
        )
    }

    suspend fun addAuthority(authority: AuthorityDto): String {
        return addAuthority(
            title = authority.title,
            authorityType = authority.authorityType,
            citation = authority.citation,
            effectiveDate = authority.effectiveDate,
            repealedDate = authority.repealedDate,
            madhhab = authority.madhhab,
            sourceUrl = null,
            chunks = emptyList()
        )
    }

    suspend fun addAuthority(
        title: String,
        authorityType: String,
        citation: String,
        effectiveDate: String?,
        repealedDate: String?,
        madhhab: String?,
        sourceUrl: String?,
        chunks: List<Map<String, String>>,
        supersededBy: String? = null
    ): String {
        val token = requireToken()
        val json = JSONObject().apply {
            put("title", title)
            put("authority_type", authorityType)
            put("citation", citation)
            if (!effectiveDate.isNullOrEmpty()) put("effective_date", effectiveDate)
            if (!repealedDate.isNullOrEmpty()) put("repealed_date", repealedDate)
            if (!madhhab.isNullOrEmpty()) put("madhhab", madhhab)
            if (!sourceUrl.isNullOrEmpty()) put("source_url", sourceUrl)
            if (!supersededBy.isNullOrEmpty()) put("superseded_by", supersededBy)
            val chunksArr = JSONArray()
            for (c in chunks) {
                chunksArr.put(JSONObject(c))
            }
            put("chunks", chunksArr)
        }.toString()

        val response = client.callEdgeFunction("litigation-manage-authority", json, token)
        refreshAll()
        return JSONObject(response).getString("authority_id")
    }

    // Pure transcription utility for the Add Legal Source dialog (mirrors
    // the web app) — turns an uploaded scan into starting draft text a
    // human still reviews before saving. Does not touch authorities/
    // authority_chunks and does not persist the file anywhere.
    suspend fun ocrLegalText(fileBase64: String, mimeType: String): String {
        val token = requireToken()
        val json = JSONObject().apply {
            put("file_base64", fileBase64)
            put("mime_type", mimeType)
        }.toString()
        val response = client.callEdgeFunction("admin-ocr-legal-text", json, token)
        return JSONObject(response).optString("text", "")
    }

    suspend fun verifyAuthority(authorityId: String, decision: String = "verified", note: String? = null) {
        val token = requireToken()
        val json = JSONObject().apply {
            put("authority_id", authorityId)
            put("decision", decision)
            if (!note.isNullOrEmpty()) put("note", note)
        }.toString()

        client.callEdgeFunction("admin-verify-authority", json, token)
        refreshAll()
    }
}
