package com.example.util

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.DeadlineDto
import com.example.data.model.HearingDto
import java.text.SimpleDateFormat
import java.util.*

object CalendarSyncManager {
    private const val TAG = "CalendarSyncManager"

    fun hasCalendarPermission(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /**
     * Finds the primary or first writable calendar ID.
     */
    private fun getWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )

        try {
            val uri: Uri = CalendarContract.Calendars.CONTENT_URI
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                var fallbackId: Long? = null
                val idCol = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val primaryCol = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val isPrimary = if (primaryCol != -1) it.getInt(primaryCol) == 1 else false
                    if (isPrimary) {
                        return id
                    }
                    if (fallbackId == null) {
                        fallbackId = id
                    }
                }
                return fallbackId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying writable calendar", e)
        }
        return null
    }

    data class SyncResult(
        val success: Boolean,
        val eventId: Long? = null,
        val message: String
    )

    /**
     * Direct sync of a single deadline to Android Calendar with reminders.
     */
    fun syncDeadlineDirect(
        context: Context,
        deadlineId: String,
        triggerEvent: String,
        dueDateStr: String,
        matterLabel: String,
        court: String? = null,
        status: String = "provisional"
    ): SyncResult {
        if (!hasCalendarPermission(context)) {
            return SyncResult(false, null, "يرجى منح إذن التقويم لمزامنة المواعيد تلقائياً.")
        }

        val calendarId = getWritableCalendarId(context)
            ?: return SyncResult(false, null, "لم يتم العثور على تقويم متاح في الجهاز.")

        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dueDate = try { sdf.parse(dueDateStr) } catch (e: Exception) { null }
                ?: return SyncResult(false, null, "تاريخ الاستحقاق غير صالح ($dueDateStr)")

            // Set start time to 9:00 AM on due date
            val calendar = Calendar.getInstance().apply {
                time = dueDate
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val startMillis = calendar.timeInMillis
            val endMillis = startMillis + (60 * 60 * 1000) // 1 hour duration

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "⚖️ [موعد إجرائي] $triggerEvent — $matterLabel")
                put(
                    CalendarContract.Events.DESCRIPTION,
                    "القضية: $matterLabel\nالمحكمة: ${court ?: "غير محددة"}\nالحدث الإجرائي: $triggerEvent\nالحالة: ${if (status == "confirmed") "مؤكد" else "مقترح"}\nتاريخ الاستحقاق: $dueDateStr\n(مزامنة من نظام هيام القضائي)"
                )
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.EVENT_LOCATION, court ?: "المحكمة المختصة")
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = eventUri?.lastPathSegment?.toLongOrNull()

            if (eventId != null) {
                // Add reminders: 1 day before (1440 mins) and 2 hours before (120 mins)
                val reminderValues1 = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 1440) // 24 hours before
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                val reminderValues2 = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 120) // 2 hours before
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                val reminderValues3 = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 4320) // 3 days before for litigation safety
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }

                try {
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues1)
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues2)
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues3)
                } catch (re: Exception) {
                    Log.w(TAG, "Failed to insert reminders: ${re.message}")
                }

                return SyncResult(true, eventId, "تمت إضافة الموعد بنجاح إلى تقويم الجهاز مع منبهات تذكيرية.")
            } else {
                return SyncResult(false, null, "فشل إنشاء الحدث في تقويم النظام.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding deadline to calendar", e)
            return SyncResult(false, null, "حدث خطأ أثناء المزامنة: ${e.localizedMessage}")
        }
    }

    /**
     * Direct sync of a Court Hearing to Android Calendar.
     */
    fun syncHearingDirect(
        context: Context,
        hearing: HearingDto,
        matterLabel: String,
        court: String?
    ): SyncResult {
        if (!hasCalendarPermission(context)) {
            return SyncResult(false, null, "يرجى منح إذن التقويم.")
        }

        val calendarId = getWritableCalendarId(context)
            ?: return SyncResult(false, null, "لم يتم العثور على تقويم متاح.")

        try {
            val dateStr = hearing.sessionDate
            val timeStr = hearing.sessionTime ?: "09:00"

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val fullDateStr = "$dateStr $timeStr"
            val sessionDate = try {
                sdf.parse(fullDateStr)
            } catch (e: Exception) {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
            } ?: return SyncResult(false, null, "تاريخ الجلسة غير صالح ($dateStr)")

            val startMillis = sessionDate.time
            val endMillis = startMillis + (2 * 60 * 60 * 1000) // 2 hours

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "🏛️ [جلسة محكمة] $matterLabel")
                put(
                    CalendarContract.Events.DESCRIPTION,
                    "القضية: $matterLabel\nالمحكمة: ${court ?: "—"}\nالدائرة: ${hearing.adjournmentReason ?: "—"}\nالتاريخ: $dateStr الساعة $timeStr\n(مزامنة من نظام هيام)"
                )
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.EVENT_LOCATION, court ?: "المحكمة")
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = eventUri?.lastPathSegment?.toLongOrNull()

            if (eventId != null) {
                val rem1 = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 1440) // 1 day before
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                val rem2 = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 120) // 2 hours before
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, rem1)
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, rem2)
                return SyncResult(true, eventId, "تمت جدولة الجلسة في التقويم بنجاح.")
            }
            return SyncResult(false, null, "فشل إنشاء الجلسة في التقويم.")
        } catch (e: Exception) {
            return SyncResult(false, null, "خطأ في المزامنة: ${e.localizedMessage}")
        }
    }

    /**
     * Intent-based Fallback / Interactive Calendar Creator:
     * Opens Google Calendar / System Calendar app with prefilled data.
     */
    fun createDeadlineCalendarIntent(
        deadline: DeadlineDto,
        matterLabel: String,
        court: String? = null
    ): Intent {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dueDate = try { sdf.parse(deadline.computedDueDate) } catch (e: Exception) { Date() }

        val startCal = Calendar.getInstance().apply {
            time = dueDate ?: Date()
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "⚖️ [موعد إجرائي] ${deadline.triggerEvent} — $matterLabel")
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "القضية: $matterLabel\nالمحكمة: ${court ?: "—"}\nالحدث الإجرائي: ${deadline.triggerEvent}\nالحالة: ${if (deadline.status == "confirmed") "مؤكد" else "مقترح"}\nتاريخ الاستحقاق: ${deadline.computedDueDate}"
            )
            putExtra(CalendarContract.Events.EVENT_LOCATION, court ?: "المحكمة")
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startCal.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startCal.timeInMillis + 3600000)
            putExtra(CalendarContract.Events.ALL_DAY, false)
        }
    }

    fun createHearingCalendarIntent(
        hearing: HearingDto,
        matterLabel: String,
        court: String? = null
    ): Intent {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sessionDate = try { sdf.parse(hearing.sessionDate) } catch (e: Exception) { Date() }

        val startCal = Calendar.getInstance().apply {
            time = sessionDate ?: Date()
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "🏛️ [جلسة محكمة] $matterLabel")
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "القضية: $matterLabel\nالمحكمة: ${court ?: "—"}\nتاريخ الجلسة: ${hearing.sessionDate} ${hearing.sessionTime ?: ""}\nالقرار/التأجيل: ${hearing.adjournmentReason ?: hearing.outcome ?: "—"}"
            )
            putExtra(CalendarContract.Events.EVENT_LOCATION, court ?: "المحكمة")
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startCal.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startCal.timeInMillis + 7200000)
            putExtra(CalendarContract.Events.ALL_DAY, false)
        }
    }

    /**
     * Bulk sync all deadlines from Room or memory to the native device calendar.
     */
    fun syncAllDeadlines(
        context: Context,
        deadlines: List<DeadlineDto>,
        mattersMap: Map<String, String>, // matterId -> matterLabel
        courtsMap: Map<String, String?> = emptyMap()
    ): Pair<Int, Int> { // Pair(successCount, failCount)
        if (!hasCalendarPermission(context)) return Pair(0, deadlines.size)

        var success = 0
        var failed = 0

        deadlines.forEach { d ->
            val matterLabel = mattersMap[d.matterId] ?: "قضية"
            val court = courtsMap[d.matterId]
            val res = syncDeadlineDirect(
                context = context,
                deadlineId = d.id,
                triggerEvent = d.triggerEvent,
                dueDateStr = d.computedDueDate,
                matterLabel = matterLabel,
                court = court,
                status = d.status
            )
            if (res.success) success++ else failed++
        }

        return Pair(success, failed)
    }
}
