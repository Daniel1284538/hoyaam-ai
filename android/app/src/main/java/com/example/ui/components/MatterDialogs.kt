package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.ui.screens.corpus.SOURCE_TYPE_LABELS
import com.example.ui.screens.matters.PARTY_ROLE_LABELS_MAP
import com.example.ui.screens.matters.STAGE_LABELS_MAP
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ==================== 1. NEW MATTER DIALOG ====================

@Composable
fun NewMatterDialog(
    onDismiss: () -> Unit,
    onSubmit: (matter: MatterDto, subjectPendingDocuments: Boolean) -> Unit
) {
    val colors = LocalHoyaamColors.current
    var label by remember { mutableStateOf("") }
    var court by remember { mutableStateOf("") }
    var circuit by remember { mutableStateOf("") }
    var caseNumber by remember { mutableStateOf("") }
    var caseYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var matterType by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("first_instance") }
    var subject by remember { mutableStateOf("") }
    // "documents" mode leaves the subject unset and opens the upload
    // dialog immediately after creation instead — same choice the web
    // app offers, so a lawyer isn't forced to type a subject by hand
    // before they can attach the case's own scanned documents.
    var subjectPendingDocuments by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("قضية جديدة", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("اسم / مسمى القضية *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("matter_label_input")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = court,
                        onValueChange = { court = it },
                        label = { Text("المحكمة *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = circuit,
                        onValueChange = { circuit = it },
                        label = { Text("الدائرة *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Required server-side (litigation-create-matter rejects
                // the request without it) — this dialog previously never
                // collected it at all, so matter creation would still
                // have failed on "matter_type is required" even after
                // fixing the wrong-endpoint bug.
                OutlinedTextField(
                    value = matterType,
                    onValueChange = { matterType = it },
                    label = { Text("نوع القضية * (مثال: مدني، جنائي، تجاري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = caseNumber,
                        onValueChange = { caseNumber = it },
                        label = { Text("رقم الدعوى *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = caseYear,
                        onValueChange = { caseYear = it },
                        label = { Text("السنة القضائية *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Stage selection
                Column {
                    Text("المرحلة القضائية:", color = colors.textDim, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        STAGE_LABELS_MAP.forEach { (key, name) ->
                            FilterChip(
                                selected = stage == key,
                                onClick = { stage = key },
                                label = { Text(name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Column {
                    Text("موضوع القضية والوقائع *", color = colors.textDim, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !subjectPendingDocuments,
                            onClick = { subjectPendingDocuments = false },
                            label = { Text("نص حر", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = subjectPendingDocuments,
                            onClick = { subjectPendingDocuments = true },
                            label = { Text("رفع مستندات بدلاً من ذلك", fontSize = 12.sp) }
                        )
                    }
                    if (!subjectPendingDocuments) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth().testTag("matter_subject_input")
                        )
                    } else {
                        Text(
                            "ستُنشأ القضية بلا موضوع نصي، وستُفتح نافذة رفع المستندات (عدة ملفات دفعة واحدة) مباشرة بعد الإنشاء — يمكن كتابة الموضوع يدوياً لاحقاً من تبويب \"نظرة عامة\".",
                            color = colors.textDim, fontSize = 12.sp
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = colors.textDim) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val matter = MatterDto(
                                id = UUID.randomUUID().toString(),
                                matterLabel = label,
                                court = court.ifBlank { null },
                                circuit = circuit.ifBlank { null },
                                caseNumber = caseNumber.ifBlank { null },
                                caseYear = caseYear.toIntOrNull(),
                                matterType = matterType.ifBlank { null },
                                stage = stage,
                                status = "active",
                                subject = if (subjectPendingDocuments) null else subject.ifBlank { null }
                            )
                            onSubmit(matter, subjectPendingDocuments)
                        },
                        // All of these are genuinely required server-side
                        // (litigation-create-matter rejects the request
                        // otherwise) — enforcing that here means a missing
                        // field is caught before a call is even made,
                        // rather than only surfacing as a Snackbar error
                        // after a round trip. subject is the one exception:
                        // required only in free-text mode, matching
                        // subject_pending_documents server-side.
                        enabled = label.isNotBlank() && court.isNotBlank() && circuit.isNotBlank() &&
                            caseNumber.isNotBlank() && caseYear.toIntOrNull() != null &&
                            matterType.isNotBlank() && (subjectPendingDocuments || subject.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text),
                        modifier = Modifier.testTag("submit_new_matter_btn")
                    ) {
                        Text("إنشاء القضية")
                    }
                }
            }
        }
    }
}

// ==================== 2. EDIT MATTER DIALOG ====================

@Composable
fun EditMatterDialog(
    matter: MatterDto,
    onDismiss: () -> Unit,
    onSubmit: (MatterDto) -> Unit
) {
    val colors = LocalHoyaamColors.current
    var label by remember { mutableStateOf(matter.matterLabel) }
    var court by remember { mutableStateOf(matter.court.orEmpty()) }
    var circuit by remember { mutableStateOf(matter.circuit.orEmpty()) }
    var caseNumber by remember { mutableStateOf(matter.caseNumber.orEmpty()) }
    var caseYear by remember { mutableStateOf(matter.caseYear?.toString().orEmpty()) }
    var subject by remember { mutableStateOf(matter.subject.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("تعديل بيانات القضية", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("اسم / مسمى القضية *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = court,
                        onValueChange = { court = it },
                        label = { Text("المحكمة") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = circuit,
                        onValueChange = { circuit = it },
                        label = { Text("الدائرة") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = caseNumber,
                        onValueChange = { caseNumber = it },
                        label = { Text("رقم الدعوى") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = caseYear,
                        onValueChange = { caseYear = it },
                        label = { Text("السنة") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("موضوع القضية") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = colors.textDim) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = matter.copy(
                                matterLabel = label,
                                court = court.ifBlank { null },
                                circuit = circuit.ifBlank { null },
                                caseNumber = caseNumber.ifBlank { null },
                                caseYear = caseYear.toIntOrNull(),
                                subject = subject.ifBlank { null }
                            )
                            onSubmit(updated)
                        },
                        enabled = label.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                    ) {
                        Text("حفظ التعديلات")
                    }
                }
            }
        }
    }
}

// ==================== 3. ADD/EDIT PARTY DIALOG ====================

@Composable
fun PartyDialog(
    initialParty: PartyDto?,
    matterId: String,
    onDismiss: () -> Unit,
    onSubmit: (PartyDto) -> Unit
) {
    val colors = LocalHoyaamColors.current
    var name by remember { mutableStateOf(initialParty?.name.orEmpty()) }
    var partyRole by remember { mutableStateOf(initialParty?.partyRole ?: "plaintiff") }
    var identifier by remember { mutableStateOf(initialParty?.identifier.orEmpty()) }
    var notes by remember { mutableStateOf(initialParty?.notes.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (initialParty == null) "إضافة طرف جديد" else "تعديل بيانات الطرف",
                    color = colors.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الطرف *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text("الصفة الإجرائية:", color = colors.textDim, fontSize = 12.sp)
                    PARTY_ROLE_LABELS_MAP.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { partyRole = key }
                        ) {
                            RadioButton(selected = partyRole == key, onClick = { partyRole = key })
                            Text(label, color = colors.text, fontSize = 13.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("الرقم القومي / السجل التجاري") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = colors.textDim) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val party = PartyDto(
                                id = initialParty?.id ?: UUID.randomUUID().toString(),
                                matterId = matterId,
                                name = name,
                                partyRole = partyRole,
                                identifier = identifier.ifBlank { null },
                                notes = notes.ifBlank { null }
                            )
                            onSubmit(party)
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                    ) {
                        Text("حفظ")
                    }
                }
            }
        }
    }
}

// ==================== 4. RECORD HEARING DIALOG ====================

@Composable
fun RecordHearingDialog(
    matterId: String,
    onDismiss: () -> Unit,
    onSubmit: (HearingDto) -> Unit
) {
    val colors = LocalHoyaamColors.current
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    var sessionDate by remember { mutableStateOf(today) }
    var sessionTime by remember { mutableStateOf("09:00") }
    var outcome by remember { mutableStateOf("adjourned") }
    var adjournmentReason by remember { mutableStateOf("") }
    var nextSessionDate by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("تسجيل جلسة", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sessionDate,
                        onValueChange = { sessionDate = it },
                        label = { Text("تاريخ الجلسة (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = sessionTime,
                        onValueChange = { sessionTime = it },
                        label = { Text("الوقت") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }

                // Outcome
                Column {
                    Text("قرار / نتيجة الجلسة:", color = colors.textDim, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("adjourned" to "تأجيل", "reserved_for_judgment" to "حجز للحكم", "judgment_issued" to "صدور حكم").forEach { (key, label) ->
                            FilterChip(
                                selected = outcome == key,
                                onClick = { outcome = key },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = adjournmentReason,
                    onValueChange = { adjournmentReason = it },
                    label = { Text("القرار / سبب التأجيل (مثال: للاطلاع والمستندات)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nextSessionDate,
                    onValueChange = { nextSessionDate = it },
                    label = { Text("تاريخ الجلسة القادمة إن وجد (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = colors.textDim) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val hearing = HearingDto(
                                id = UUID.randomUUID().toString(),
                                matterId = matterId,
                                sessionDate = sessionDate,
                                sessionTime = sessionTime.ifBlank { null },
                                outcome = outcome,
                                adjournmentReason = adjournmentReason.ifBlank { null },
                                nextSessionDate = nextSessionDate.ifBlank { null }
                            )
                            onSubmit(hearing)
                        },
                        enabled = sessionDate.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                    ) {
                        Text("تسجيل")
                    }
                }
            }
        }
    }
}

// ==================== 5. PROPOSE DEADLINE DIALOG ====================

// Rebuilt around real deadline_rules rows instead of a free-text "legal
// basis" field and a client-computed day offset. litigation-propose-
// deadline requires a real rule_id and computes the due date itself,
// server-side, via fn_compute_deadline — deterministically, and only
// against a rule that is actually active (a lawyer has signed off on
// it). Sending a made-up day count and a text label that merely looks
// like a citation bypassed that entirely; there is no due date shown
// here anymore because this dialog no longer computes one — the
// confirmation of what was actually computed happens after submission,
// from the server's own response.
@Composable
fun ProposeDeadlineDialog(
    matterId: String,
    rules: List<DeadlineRuleDto>,
    onDismiss: () -> Unit,
    onSubmit: (ruleId: String, triggerEvent: String, triggerDate: String) -> Unit
) {
    val colors = LocalHoyaamColors.current
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    var selectedRule by remember { mutableStateOf(rules.firstOrNull()) }
    var triggerEvent by remember { mutableStateOf("") }
    var triggerDate by remember { mutableStateOf(today) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("اقتراح موعد إجرائي حتمي", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)

                if (rules.isEmpty()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = colors.warn.copy(alpha = 0.1f), border = BorderStroke(1.dp, colors.warn.copy(alpha = 0.3f))) {
                        Text(
                            text = "لا توجد قواعد مواعيد معتمدة بعد — لا يمكن اقتراح أي موعد قبل أن يوقّع محامٍ على قاعدة واحدة على الأقل.",
                            color = colors.warn,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                } else {
                    Text("القاعدة الإجرائية المعتمدة *:", color = colors.textDim, fontSize = 12.sp)
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(rules) { rule ->
                            val isSelected = selectedRule?.id == rule.id
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.inset,
                                border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.border),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedRule = rule
                                    if (triggerEvent.isBlank()) triggerEvent = rule.titleAr ?: rule.ruleKey
                                }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(rule.titleAr ?: rule.ruleKey, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                                    Text(
                                        text = "${rule.durationValue ?: "?"} ${rule.durationUnit ?: ""} — ${rule.citation ?: ""}",
                                        color = colors.textDim,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = triggerEvent,
                    onValueChange = { triggerEvent = it },
                    label = { Text("وصف الحدث الموجب للميعاد لهذه القضية *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = triggerDate,
                    onValueChange = { triggerDate = it },
                    label = { Text("تاريخ الحدث المحرِّك (YYYY-MM-DD) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = colors.textDim) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val rule = selectedRule ?: return@Button
                            onSubmit(rule.id, triggerEvent, triggerDate)
                        },
                        enabled = selectedRule != null && triggerEvent.isNotBlank() && triggerDate.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                    ) {
                        Text("اقتراح الميعاد")
                    }
                }
            }
        }
    }
}

// ==================== 6. UPLOAD DOCS DIALOG ====================

// Real files now (PDF/image), matching the web app's upload dialog
// exactly: pick multiple at once, upload+extract them one at a time
// (legal-ingest -> storage -> legal-extract), each row tracking its own
// stage independently so one failure doesn't block the rest of the batch.
// The old version had no file picker at all — it only accepted a filename
// typed by hand plus text pasted in manually, with a "document type" that
// legal-ingest doesn't even have a field for.
private data class UploadRow(
    val uri: android.net.Uri,
    val name: String,
    val mimeType: String,
    val stage: String? = null, // null|"uploading"|"extracting"|"done"|"failed"
    val errorMsg: String? = null
)

private val FILE_STAGE_LABELS = mapOf(
    "uploading" to "رفع",
    "extracting" to "استخراج (Gemini)",
    "done" to "تم",
    "failed" to "فشل"
)

@Composable
fun UploadDocsDialog(
    matterId: String,
    onDismiss: () -> Unit,
    onUploadFile: suspend (filename: String, mimeType: String, bytes: ByteArray) -> Result<Unit>,
    onAllDone: () -> Unit
) {
    val colors = LocalHoyaamColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<UploadRow>>(emptyList()) }
    var started by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun resolveName(uri: android.net.Uri): String {
        var name = uri.lastPathSegment ?: "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx)?.let { name = it }
        }
        return name
    }

    val pickFiles = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        rows = uris.map { uri ->
            UploadRow(
                uri = uri,
                name = resolveName(uri),
                mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            )
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("رفع مستندات", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)

                error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }

                if (!started) {
                    OutlinedButton(
                        onClick = { pickFiles.launch(arrayOf("application/pdf", "image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("اختيار الملفات (PDF أو صور) — يمكن اختيار عدة ملفات دفعة واحدة")
                    }
                }

                if (rows.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                                    .background(colors.inset, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.name, color = colors.text, fontSize = 13.sp, maxLines = 1)
                                    row.errorMsg?.let { Text(it, color = colors.danger, fontSize = 11.sp) }
                                }
                                if (!started) {
                                    IconButton(onClick = { rows = rows.filter { it.uri != row.uri } }) {
                                        Icon(Icons.Filled.Close, contentDescription = "إزالة", tint = colors.textDim)
                                    }
                                } else {
                                    val label = FILE_STAGE_LABELS[row.stage] ?: "قيد الانتظار"
                                    Text(
                                        label,
                                        color = if (row.stage == "failed") colors.danger else if (row.stage == "done") colors.text else colors.textDim,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text(if (started) "إغلاق" else "إلغاء", color = colors.textDim) }
                    if (!started) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (rows.isEmpty()) { error = "اختر ملفاً واحداً على الأقل"; return@Button }
                                error = null
                                started = true
                                busy = true
                                scope.launch {
                                    for (row in rows) {
                                        rows = rows.map { if (it.uri == row.uri) it.copy(stage = "uploading") else it }
                                        try {
                                            val bytes = context.contentResolver.openInputStream(row.uri)?.use { it.readBytes() }
                                                ?: throw java.io.IOException("تعذّرت قراءة الملف")
                                            rows = rows.map { if (it.uri == row.uri) it.copy(stage = "extracting") else it }
                                            val result = onUploadFile(row.name, row.mimeType, bytes)
                                            rows = rows.map {
                                                if (it.uri != row.uri) it
                                                else if (result.isSuccess) it.copy(stage = "done")
                                                else it.copy(stage = "failed", errorMsg = result.exceptionOrNull()?.message ?: "فشل الرفع")
                                            }
                                        } catch (e: Exception) {
                                            rows = rows.map { if (it.uri == row.uri) it.copy(stage = "failed", errorMsg = e.message) else it }
                                        }
                                    }
                                    busy = false
                                    onAllDone()
                                }
                            },
                            enabled = rows.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                        ) {
                            Text("رفع")
                        }
                    }
                }
            }
        }
    }
}

// ==================== 7. NEW DRAFT / LEGAL MEMO DIALOG ====================

@Composable
fun NewDraftDialog(
    matterId: String,
    authorities: List<AuthorityDto>,
    isMemo: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (docType: String, instructions: String, claims: List<String>, selectedAuthorityIds: List<String>) -> Unit
) {
    val colors = LocalHoyaamColors.current
    var docType by remember { mutableStateOf(if (isMemo) "legal_memo" else "statement_of_claim") }
    var instructions by remember { mutableStateOf("") }
    var claimsText by remember { mutableStateOf("") }
    val selectedAuthorityIds = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(0.95f).verticalScroll(rememberScrollState()).padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isMemo) "صياغة مذكرة قانونية متكاملة (IRAC)" else "صياغة مسودة بالذكاء الاصطناعي",
                    color = colors.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )

                if (isMemo) {
                    OutlinedTextField(
                        value = claimsText,
                        onValueChange = { claimsText = it },
                        label = { Text("الطلبات والدفوع القانونية (سطر لكل طلب) *") },
                        placeholder = { Text("أولاً: بطلان إعلان صحيفة الدعوى\nثانياً: رفض الدعوى لانتفاء صفة المدعي") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("المصادر القانونية المعتمدة للاستشهاد بها:", color = colors.textDim, fontSize = 12.sp)
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(authorities) { auth ->
                            val isSelected = selectedAuthorityIds.contains(auth.id)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.inset,
                                border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.border),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (isSelected) selectedAuthorityIds.remove(auth.id)
                                    else selectedAuthorityIds.add(auth.id)
                                }
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isSelected, onCheckedChange = {
                                        if (it) selectedAuthorityIds.add(auth.id)
                                        else selectedAuthorityIds.remove(auth.id)
                                    })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${auth.title} (${auth.citation})", color = colors.text, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("تعليمات إضافية للصياغة") },
                    placeholder = { Text("التركيز على نصوص القانون المدني ودفوع البطلان...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = colors.textDim) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val claims = claimsText.lines().filter { it.isNotBlank() }
                            onSubmit(docType, instructions, claims, selectedAuthorityIds.toList())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                    ) {
                        Text("توليد المسودة")
                    }
                }
            }
        }
    }
}

// ==================== 8. FILL TEMPLATE DIALOG ====================

@Composable
fun FillTemplateDialog(
    templates: List<TemplateDto>,
    onDismiss: () -> Unit,
    onSubmit: (templateId: String, fieldValues: Map<String, String>) -> Unit
) {
    val colors = LocalHoyaamColors.current
    var selectedTemplate by remember { mutableStateOf(templates.firstOrNull()) }
    val fieldValues = remember { mutableStateMapOf<String, String>() }

    val placeholders = remember(selectedTemplate) {
        val regex = Regex("\\{\\{([a-zA-Z0-9_]+)\\}\\}")
        val body = selectedTemplate?.contentText
        if (body != null) {
            regex.findAll(body).map { it.groupValues[1] }.distinct().toList()
        } else {
            emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ملء قالب صياغة معتمد", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)

                Text("اختر القالب:", color = colors.textDim, fontSize = 12.sp)
                templates.forEach { tmpl ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedTemplate = tmpl }
                    ) {
                        RadioButton(selected = selectedTemplate?.id == tmpl.id, onClick = { selectedTemplate = tmpl })
                        Text(tmpl.title, color = colors.text, fontSize = 13.sp)
                    }
                }

                if (placeholders.isNotEmpty()) {
                    Text("الحقول المطلوبة للقالب:", color = colors.textDim, fontSize = 12.sp)
                    placeholders.forEach { field ->
                        OutlinedTextField(
                            value = fieldValues[field].orEmpty(),
                            onValueChange = { fieldValues[field] = it },
                            label = { Text(field) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = colors.textDim) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedTemplate?.let { tmpl ->
                                onSubmit(tmpl.id, fieldValues.toMap())
                            }
                        },
                        enabled = selectedTemplate != null,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                    ) {
                        Text("ملء القالب وإنشاء المسودة")
                    }
                }
            }
        }
    }
}

// ==================== 9. CITATION INSPECTOR DIALOG ====================

// Rebuilt against the real draft_citations table instead of a client-side
// regex over the draft's raw text fuzzy-matched against locally loaded
// authorities. That approach had no connection to the actual server-side
// binding (a citation bound to a real retrieved authority_chunk_id at
// draft-generation time) or to litigation-verify-citation — it could miss
// real bound citations, produce false "matches" on ordinary prose that
// merely looked citation-shaped, and had no way to actually record a
// lawyer's verify/flag decision. This dialog now shows the real bindings
// and calls the real endpoint.
@Composable
fun CitationInspectorDialog(
    draft: DraftDto,
    citations: List<DraftCitationDto>,
    isLoading: Boolean,
    onVerify: (citationId: String, status: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalHoyaamColors.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("فاحص الاستشهادات القانونية", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = colors.textDim)
                    }
                }

                Text(
                    text = "كل استشهاد هنا مأخوذ من سجلات المسودة الفعلية على الخادم، مرتبطاً بالمقطع القانوني الحقيقي الذي استُخرج منه إن وُجد. التوثيق أو التعليم هنا يُسجَّل فوراً على الخادم ويمنع تصدير المسودة كملف Word حتى تُحسم كل الاستشهادات.",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    fontFamily = ArabicSansFontFamily
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                } else if (citations.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.inset,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "لا توجد استشهادات مسجّلة على هذه المسودة.",
                            color = colors.textDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp),
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(citations, key = { it.id }) { citation ->
                            val chunk = citation.authorityChunks
                            val auth = chunk?.authorities
                            val isBound = citation.authorityChunkId != null
                            val isVerified = citation.status == "verified"
                            val isFlagged = citation.status == "flagged"
                            val isRepealed = auth?.repealedDate != null

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.inset,
                                border = BorderStroke(1.dp, if (isRepealed || isFlagged) colors.danger else if (isVerified) colors.good else colors.warn)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(citation.citationText, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        if (isRepealed) DangerChip(text = "ملغى / معدل")
                                        else if (isFlagged) DangerChip(text = "معلَّم كمتنازع عليه")
                                        else if (isVerified) ConfirmedChip(text = "موثّق")
                                        else if (isBound) ProvisionalChip(text = "غير موثّق بعد")
                                        else DangerChip(text = "غير مرتبط بمقطع مسترجَع")
                                    }

                                    if (chunk != null && auth != null) {
                                        Text(
                                            text = "السند: ${auth.title ?: ""} — ${auth.citation ?: ""}",
                                            color = colors.accent,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        if (!chunk.chunkText.isNullOrBlank()) {
                                            Text(
                                                text = chunk.chunkText,
                                                color = colors.textDim,
                                                fontSize = 11.sp,
                                                maxLines = 3,
                                                modifier = Modifier.padding(top = 2.dp),
                                                fontFamily = ArabicSansFontFamily
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "غير مرتبط بأي مقطع قانوني مسترجَع — لا يمكن توثيقه، يمكن فقط تعليمه كمتنازع عليه.",
                                            color = colors.danger,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 4.dp),
                                            fontFamily = ArabicSansFontFamily
                                        )
                                    }

                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onVerify(citation.id, "verified") },
                                            enabled = isBound && !isVerified,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("توثيق", fontSize = 12.sp) }
                                        OutlinedButton(
                                            onClick = { onVerify(citation.id, "flagged") },
                                            enabled = !isFlagged,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("تعليم كمتنازع عليه", fontSize = 12.sp) }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = colors.text)) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }
}

// ==================== 10. ADD AUTHORITY DIALOG ====================

// A chunk of source text, either extracted from an uploaded scan (OCR via
// admin-ocr-legal-text) or typed in by hand — same two paths the web app
// offers. At least one non-empty chunk is required before saving.
private data class AuthorityChunk(
    val localId: Int,
    val source: String, // "file" | "manual"
    val fileName: String? = null,
    val chunkRef: String = "",
    val chunkText: String = "",
    val status: String = "done", // "uploading" | "done" | "failed"
    val errorMsg: String? = null
)

@Composable
fun AddAuthorityDialog(
    onDismiss: () -> Unit,
    onExtractText: suspend (fileBase64: String, mimeType: String) -> Result<String>,
    onSubmit: (
        title: String,
        authorityType: String,
        citation: String,
        effectiveDate: String?,
        repealedDate: String?,
        supersededBy: String?,
        madhhab: String?,
        sourceUrl: String?,
        chunks: List<Pair<String, String>>
    ) -> Unit
) {
    val colors = LocalHoyaamColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var citation by remember { mutableStateOf("") }
    var authorityType by remember { mutableStateOf("statute") }
    var madhhab by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var effectiveDate by remember { mutableStateOf("") }
    var repealedDate by remember { mutableStateOf("") }
    var supersededBy by remember { mutableStateOf("") }
    var manualText by remember { mutableStateOf("") }
    var manualRef by remember { mutableStateOf("") }
    var showRepealSection by remember { mutableStateOf(false) }

    var chunks by remember { mutableStateOf<List<AuthorityChunk>>(emptyList()) }
    var chunkSeq by remember { mutableStateOf(0) }
    var uploadBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val hasContent = chunks.any { it.chunkText.isNotBlank() }

    fun resolveName(uri: android.net.Uri): String {
        var name = uri.lastPathSegment ?: "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx)?.let { name = it }
        }
        return name
    }

    val pickFiles = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uploadBusy = true
        val newRows = uris.map { uri ->
            chunkSeq += 1
            AuthorityChunk(localId = chunkSeq, source = "file", fileName = resolveName(uri), chunkRef = resolveName(uri), status = "uploading")
                .let { it to uri }
        }
        chunks = chunks + newRows.map { it.first }
        scope.launch {
            for ((row, uri) in newRows) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw java.io.IOException("تعذّرت قراءة الملف")
                    val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val result = onExtractText(base64, mimeType)
                    chunks = chunks.map {
                        if (it.localId != row.localId) it
                        else if (result.isSuccess) it.copy(chunkText = result.getOrDefault(""), status = "done")
                        else it.copy(status = "failed", errorMsg = result.exceptionOrNull()?.message)
                    }
                } catch (e: Exception) {
                    chunks = chunks.map { if (it.localId == row.localId) it.copy(status = "failed", errorMsg = e.message) else it }
                }
            }
            uploadBusy = false
        }
    }

    Dialog(onDismissRequest = { if (!uploadBusy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إضافة مصدر قانوني", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)
                Text(
                    "النص يجب أن يكون نصاً قانونياً حقيقياً موثّقاً. يبقى المصدر \"غير موثّق\" حتى يراجعه شخص آخر غيرك.",
                    color = colors.textDim, fontSize = 12.sp
                )
                error?.let { Text(it, color = colors.danger, fontSize = 13.sp) }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text("النوع:", color = colors.textDim, fontSize = 12.sp)
                    SOURCE_TYPE_LABELS.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { authorityType = key }
                        ) {
                            RadioButton(selected = authorityType == key, onClick = { authorityType = key })
                            Text(label, color = colors.text, fontSize = 13.sp)
                        }
                    }
                }

                if (authorityType == "fiqh_doctrine") {
                    OutlinedTextField(
                        value = madhhab,
                        onValueChange = { madhhab = it },
                        label = { Text("المذهب *") },
                        placeholder = { Text("hanafi / maliki / shafii / hanbali") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = citation,
                    onValueChange = { citation = it },
                    label = { Text("الاستشهاد * (مثال: قانون المرافعات، م. 123)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = effectiveDate,
                    onValueChange = { effectiveDate = it },
                    label = { Text("تاريخ النفاذ (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    label = { Text("رابط المصدر (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ---- upload / manual text, same two paths as the web dialog ----
                Text("النص * — ارفع ملفات (PDF أو صور، عدة ملفات دفعة واحدة) أو أضف نصاً يدوياً", color = colors.textDim, fontSize = 12.sp)
                OutlinedButton(
                    onClick = { pickFiles.launch(arrayOf("application/pdf", "image/*")) },
                    enabled = !uploadBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("اختيار ملفات") }
                if (uploadBusy) Text("جارٍ استخراج النص من الملفات…", color = colors.textDim, fontSize = 12.sp)

                chunks.forEach { c ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(colors.inset, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(c.fileName ?: "نص يدوي", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (c.status == "uploading") Text("جارٍ الاستخراج…", color = colors.textDim, fontSize = 11.sp)
                            if (c.status == "failed") Text("فشل الاستخراج", color = colors.danger, fontSize = 11.sp)
                            IconButton(onClick = { chunks = chunks.filter { it.localId != c.localId } }, enabled = !uploadBusy) {
                                Icon(Icons.Filled.Close, contentDescription = "إزالة", tint = colors.textDim)
                            }
                        }
                        c.errorMsg?.let { Text(it, color = colors.danger, fontSize = 11.sp) }
                        OutlinedTextField(
                            value = c.chunkRef,
                            onValueChange = { v -> chunks = chunks.map { if (it.localId == c.localId) it.copy(chunkRef = v) else it } },
                            label = { Text("إشارة الفقرة/المادة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = c.chunkText,
                            onValueChange = { v -> chunks = chunks.map { if (it.localId == c.localId) it.copy(chunkText = v) else it } },
                            label = { Text("النص (قابل للتحقق والتعديل قبل الحفظ)") },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column {
                    Text("أضف نصاً يدوياً", color = colors.textDim, fontSize = 12.sp)
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualRef,
                            onValueChange = { manualRef = it },
                            label = { Text("إشارة الفقرة/المادة (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (manualText.isBlank()) return@Button
                                chunkSeq += 1
                                chunks = chunks + AuthorityChunk(localId = chunkSeq, source = "manual", chunkRef = manualRef.trim(), chunkText = manualText.trim())
                                manualText = ""; manualRef = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                        ) { Text("+ إضافة") }
                    }
                }

                TextButton(onClick = { showRepealSection = !showRepealSection }) {
                    Text("ملغى بالفعل؟ (اختياري)", color = colors.textDim, fontSize = 12.sp)
                }
                if (showRepealSection) {
                    OutlinedTextField(
                        value = repealedDate,
                        onValueChange = { repealedDate = it },
                        label = { Text("تاريخ الإلغاء (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supersededBy,
                        onValueChange = { supersededBy = it },
                        label = { Text("حل محله (UUID)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !uploadBusy) { Text("إلغاء", color = colors.textDim) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            error = null
                            if (title.isBlank() || citation.isBlank()) { error = "العنوان والاستشهاد مطلوبان"; return@Button }
                            if (!hasContent) { error = "أضف نصاً واحداً على الأقل — ارفع ملفاً أو اكتب نصاً يدوياً"; return@Button }
                            if (authorityType == "fiqh_doctrine" && madhhab.isBlank()) { error = "المذهب مطلوب للمصادر الفقهية"; return@Button }
                            onSubmit(
                                title,
                                authorityType,
                                citation,
                                effectiveDate.ifBlank { null },
                                repealedDate.ifBlank { null },
                                supersededBy.ifBlank { null },
                                madhhab.ifBlank { null },
                                sourceUrl.ifBlank { null },
                                chunks.filter { it.chunkText.isNotBlank() }.map { it.chunkText.trim() to it.chunkRef.trim() }
                            )
                        },
                        enabled = !uploadBusy && title.isNotBlank() && citation.isNotBlank() && hasContent,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                    ) {
                        Text("حفظ")
                    }
                }
            }
        }
    }
}

// ==================== 11. ADD TEMPLATE DIALOG ====================

@Composable
fun AddTemplateDialog(
    onDismiss: () -> Unit,
    onSubmit: (TemplateDto) -> Unit
) {
    val colors = LocalHoyaamColors.current
    var name by remember { mutableStateOf("") }
    var docType by remember { mutableStateOf("صحيفة دعوى") }
    var bodyTemplate by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إضافة قالب صياغة جديد", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم القالب *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = docType,
                    onValueChange = { docType = it },
                    label = { Text("نوع المستند (مثال: صحيفة دعوى، استئناف)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bodyTemplate,
                    onValueChange = { bodyTemplate = it },
                    label = { Text("نص القالب (استخدم {{متغير}} للحقول الديناميكية) *") },
                    placeholder = { Text("أنه في يوم {{day}} الموافق {{date}}\nبناءً على طلب السيد / {{plaintiff_name}}...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = colors.textDim) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val tmpl = TemplateDto(
                                id = UUID.randomUUID().toString(),
                                title = name,
                                docType = docType,
                                contentText = bodyTemplate
                            )
                            onSubmit(tmpl)
                        },
                        enabled = name.isNotBlank() && bodyTemplate.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text)
                    ) {
                        Text("حفظ القالب")
                    }
                }
            }
        }
    }
}

// ==================== 11. SUMMARY RESULT DIALOG ====================
// litigation-summarize — previously never wired into this app at all.
// Shows either a case-wide or a single-document summary, whichever the
// server returned (SummarizeResponse.scope/documentId tell which); the
// server's own `note` covers the "nothing to summarize yet" case (e.g.
// no extracted documents), which is shown as-is rather than as an error.

@Composable
fun SummaryResultDialog(
    result: SummarizeResponse,
    onDismiss: () -> Unit
) {
    val colors = LocalHoyaamColors.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (result.scope == "document") "تلخيص المستند" else "التلخيص التنفيذي للقضية",
                        color = colors.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AmiriFontFamily
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = colors.textDim)
                    }
                }

                if (result.error != null) {
                    Text(result.error, color = colors.danger, fontSize = 13.sp)
                } else if (!result.note.isNullOrBlank() && result.summary.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = colors.inset, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = result.note,
                            color = colors.textDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp),
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                } else {
                    if (result.truncatedContext) {
                        Surface(shape = RoundedCornerShape(6.dp), color = colors.warn.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "تنبيه: تم تقصير السياق المرسل للنموذج — قد لا يغطي الملخص كل المستندات.",
                                color = colors.warn,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }

                    if (!result.summary.isNullOrBlank()) {
                        Text(
                            text = result.summary,
                            color = colors.text,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            fontFamily = ArabicSansFontFamily
                        )
                    }

                    if (result.keyPoints.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("أبرز النقاط", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                            result.keyPoints.forEach { point ->
                                Row {
                                    Text("• ", color = colors.textDim, fontSize = 13.sp)
                                    Text(point, color = colors.text, fontSize = 13.sp, fontFamily = ArabicSansFontFamily, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (result.flags.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("تنبيهات", color = colors.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                            result.flags.forEach { flag ->
                                Row {
                                    Text("• ", color = colors.danger, fontSize = 13.sp)
                                    Text(flag, color = colors.danger, fontSize = 13.sp, fontFamily = ArabicSansFontFamily, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = colors.text)) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }
}

// ==================== 12. CONFLICT CHECK DIALOG ====================
// litigation-check-conflicts — previously never wired into this app at
// all. Lets the user type one or more names to screen (pre-filled from
// this matter's current parties as a starting point, editable), and
// shows the server's fuzzy-matched conflicts against the rest of the
// firm's matters (excluding this one, via excludeMatterId).

@Composable
fun ConflictCheckDialog(
    initialNames: List<String>,
    isLoading: Boolean,
    result: ConflictCheckResponse?,
    onCheck: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalHoyaamColors.current
    var namesText by remember { mutableStateOf(initialNames.joinToString("\n")) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("فحص تعارض المصالح", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AmiriFontFamily)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = colors.textDim)
                    }
                }

                Text(
                    text = "يُدخل اسم واحد في كل سطر. يبحث الفحص عن تطابقات تقريبية بين هذه الأسماء وأطراف بقية قضايا المكتب.",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    fontFamily = ArabicSansFontFamily
                )

                OutlinedTextField(
                    value = namesText,
                    onValueChange = { namesText = it },
                    label = { Text("الأسماء (سطر لكل اسم) *") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            val names = namesText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                            if (names.isNotEmpty()) onCheck(names)
                        },
                        enabled = !isLoading && namesText.split("\n").any { it.trim().isNotBlank() },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (isLoading) "جارٍ الفحص…" else "فحص", color = Color.White)
                    }
                }

                if (result != null) {
                    if (result.error != null) {
                        Text(result.error, color = colors.danger, fontSize = 13.sp)
                    } else if (result.matches.isEmpty()) {
                        Surface(shape = RoundedCornerShape(8.dp), color = colors.good.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "لا توجد تطابقات محتملة في قضايا المكتب الأخرى.",
                                color = colors.good,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp),
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    } else {
                        if (!result.overallRecommendation.isNullOrBlank()) {
                            Surface(shape = RoundedCornerShape(8.dp), color = colors.warn.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = result.overallRecommendation,
                                    color = colors.warn,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(12.dp),
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.matches.forEach { match ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.inset,
                                    border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(match.matchedName, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            DangerChip(text = "${(match.score * 100).toInt()}٪ تطابق")
                                        }
                                        Text(
                                            text = "${match.matterLabel} — ${PARTY_ROLE_LABELS_MAP[match.partyRole] ?: match.partyRole}",
                                            color = colors.textDim,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 2.dp),
                                            fontFamily = ArabicSansFontFamily
                                        )
                                        if (!match.identifier.isNullOrBlank()) {
                                            Text(
                                                text = "الرقم التعريفي: ${match.identifier}",
                                                color = colors.text2,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        result.aiNotes?.forEach { note ->
                            Text("• $note", color = colors.textDim, fontSize = 11.sp, fontFamily = ArabicSansFontFamily)
                        }
                        if (!result.aiError.isNullOrBlank()) {
                            Text(result.aiError, color = colors.textDim, fontSize = 11.sp, fontFamily = ArabicSansFontFamily)
                        }
                    }
                }
            }
        }
    }
}
