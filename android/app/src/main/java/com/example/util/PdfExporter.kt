package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entities.LocalCaseNoteEntity
import com.example.data.local.entities.LocalCaseSummaryEntity
import com.example.data.local.entities.LocalScannedDocumentEntity
import com.example.data.model.DeadlineDto
import com.example.data.model.HearingDto
import com.example.data.model.MatterDto
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExporter {

    private const val PAGE_WIDTH = 595 // Standard A4 points at 72dpi
    private const val PAGE_HEIGHT = 842 // Standard A4 points at 72dpi
    private const val MARGIN = 36f

    fun generateCasePdf(
        context: Context,
        matter: MatterDto,
        summary: LocalCaseSummaryEntity?,
        notes: List<LocalCaseNoteEntity>,
        scans: List<LocalScannedDocumentEntity>,
        hearings: List<HearingDto> = emptyList(),
        deadlines: List<DeadlineDto> = emptyList()
    ): File? {
        val document = PdfDocument()

        try {
            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var yPos = MARGIN

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(33, 27, 20)
                textSize = 17f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }

            val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(108, 93, 47) // Gold/Antique Brass
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(40, 36, 30)
                textSize = 9.5f
                typeface = Typeface.DEFAULT
            }

            val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 25, 20)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
            }

            val dimPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(110, 105, 95)
                textSize = 8.5f
                typeface = Typeface.DEFAULT
            }

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.rgb(215, 205, 190)
            }

            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.rgb(108, 93, 47)
            }

            val contentWidth = (PAGE_WIDTH - (MARGIN * 2)).toInt()

            fun checkPageSpace(requiredHeight: Float) {
                if (yPos + requiredHeight > PAGE_HEIGHT - MARGIN - 25f) {
                    // Draw footer on current page
                    drawFooter(canvas, currentPageNumber, dimPaint)
                    document.finishPage(page)

                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    yPos = MARGIN + 10f

                    // Draw mini header on subsequent pages
                    drawMiniHeader(canvas, matter.matterLabel, subtitlePaint, dimPaint)
                    yPos += 30f
                }
            }

            // 1. MAIN HEADER
            bgPaint.color = Color.rgb(246, 241, 232)
            canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + 65f), 8f, 8f, bgPaint)
            borderPaint.color = Color.rgb(210, 195, 170)
            canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + 65f), 8f, 8f, borderPaint)

            // Accent bar on right (RTL indicator)
            canvas.drawRoundRect(RectF(PAGE_WIDTH - MARGIN - 6f, yPos, PAGE_WIDTH - MARGIN, yPos + 65f), 4f, 4f, accentPaint)

            val headerTitleLayout = createStaticLayout("هيام للتقاضي - تقرير ملف القضية والمذكرات", titlePaint, contentWidth - 30)
            canvas.save()
            canvas.translate(MARGIN + 12f, yPos + 10f)
            headerTitleLayout.draw(canvas)
            canvas.restore()

            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            val exportTimeStr = "تاريخ التقرير: ${sdf.format(Date())} • مستند رسمي للأرشفة والمرافعة"
            val timeLayout = createStaticLayout(exportTimeStr, dimPaint, contentWidth - 30)
            canvas.save()
            canvas.translate(MARGIN + 12f, yPos + 38f)
            timeLayout.draw(canvas)
            canvas.restore()

            yPos += 75f

            // 2. CASE METADATA BOX
            checkPageSpace(110f)
            bgPaint.color = Color.rgb(255, 255, 255)
            borderPaint.color = Color.rgb(220, 212, 198)
            canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + 95f), 6f, 6f, bgPaint)
            canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + 95f), 6f, 6f, borderPaint)

            val metaHeaderLayout = createStaticLayout("بيانات الدعوى الأساسية", subtitlePaint, contentWidth - 20)
            canvas.save()
            canvas.translate(MARGIN + 12f, yPos + 8f)
            metaHeaderLayout.draw(canvas)
            canvas.restore()

            val row1 = "اسم الدعوى: ${matter.matterLabel}    |    رقم الدعوى: ${matter.caseNumber ?: "—"} لسنة ${matter.caseYear ?: "—"}"
            val row1Layout = createStaticLayout(row1, boldPaint, contentWidth - 24)
            canvas.save()
            canvas.translate(MARGIN + 12f, yPos + 28f)
            row1Layout.draw(canvas)
            canvas.restore()

            val row2 = "المحكمة: ${matter.court ?: "غير محدد"}    |    الدائرة: ${matter.circuit ?: "غير محدد"}"
            val row2Layout = createStaticLayout(row2, bodyPaint, contentWidth - 24)
            canvas.save()
            canvas.translate(MARGIN + 12f, yPos + 48f)
            row2Layout.draw(canvas)
            canvas.restore()

            val stageLabel = when (matter.stage) {
                "first_instance" -> "أول درجة (ابتدائي)"
                "appeal" -> "استئناف عالي"
                "cassation" -> "محكمة النقض"
                else -> matter.stage ?: "قيد المباشرة"
            }
            val statusLabel = if (matter.status == "active") "سارية ومستمرة" else matter.status
            val row3 = "المرحلة القضائية: $stageLabel    |    الحالة: $statusLabel"
            val row3Layout = createStaticLayout(row3, dimPaint, contentWidth - 24)
            canvas.save()
            canvas.translate(MARGIN + 12f, yPos + 68f)
            row3Layout.draw(canvas)
            canvas.restore()

            yPos += 105f

            // 3. CASE SUMMARY & AI INSIGHTS
            val summaryText = summary?.summaryText ?: matter.subject ?: "لا يوجد ملخص مسجل لهذه الدعوى."
            val summaryLayout = createStaticLayout(summaryText, bodyPaint, contentWidth - 24)
            val summaryBoxHeight = 40f + summaryLayout.height

            checkPageSpace(summaryBoxHeight + 10f)

            bgPaint.color = Color.rgb(250, 247, 240)
            canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + summaryBoxHeight), 6f, 6f, bgPaint)
            borderPaint.color = Color.rgb(220, 210, 195)
            canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + summaryBoxHeight), 6f, 6f, borderPaint)

            val summaryHeaderLayout = createStaticLayout("موجز موضوع الدعوى والوقائع (محفوظ محلياً)", subtitlePaint, contentWidth - 20)
            canvas.save()
            canvas.translate(MARGIN + 12f, yPos + 8f)
            summaryHeaderLayout.draw(canvas)
            canvas.restore()

            canvas.save()
            canvas.translate(MARGIN + 12f, yPos + 28f)
            summaryLayout.draw(canvas)
            canvas.restore()

            yPos += summaryBoxHeight + 14f

            // AI Analysis if present
            if (!summary?.aiAnalysisSummary.isNullOrBlank()) {
                val aiLayout = createStaticLayout(summary!!.aiAnalysisSummary!!, bodyPaint, contentWidth - 24)
                val aiBoxHeight = 35f + aiLayout.height
                checkPageSpace(aiBoxHeight + 10f)

                bgPaint.color = Color.rgb(244, 247, 252)
                canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + aiBoxHeight), 6f, 6f, bgPaint)
                borderPaint.color = Color.rgb(195, 210, 230)
                canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + aiBoxHeight), 6f, 6f, borderPaint)

                val aiHeaderLayout = createStaticLayout("التحليل القانوني والإجرائي (الذكاء الاصطناعي)", subtitlePaint, contentWidth - 20)
                canvas.save()
                canvas.translate(MARGIN + 12f, yPos + 8f)
                aiHeaderLayout.draw(canvas)
                canvas.restore()

                canvas.save()
                canvas.translate(MARGIN + 12f, yPos + 26f)
                aiLayout.draw(canvas)
                canvas.restore()

                yPos += aiBoxHeight + 14f
            }

            // 4. LAWYER NOTES SECTION
            checkPageSpace(50f)
            val notesSecHeaderLayout = createStaticLayout("مذكرات وملاحظات المحامي المسجلة (${notes.size})", subtitlePaint, contentWidth)
            canvas.save()
            canvas.translate(MARGIN, yPos)
            notesSecHeaderLayout.draw(canvas)
            canvas.restore()
            yPos += 24f

            if (notes.isEmpty()) {
                checkPageSpace(35f)
                val emptyNotesLayout = createStaticLayout("لا توجد ملاحظات مسجلة لهذه القضية حتى تاريخه.", dimPaint, contentWidth)
                canvas.save()
                canvas.translate(MARGIN, yPos)
                emptyNotesLayout.draw(canvas)
                canvas.restore()
                yPos += 30f
            } else {
                notes.forEachIndexed { index, note ->
                    val noteContentLayout = createStaticLayout(note.content, bodyPaint, contentWidth - 24)
                    val noteCardHeight = 45f + noteContentLayout.height

                    checkPageSpace(noteCardHeight + 8f)

                    bgPaint.color = if (note.isPinned) Color.rgb(255, 253, 247) else Color.rgb(255, 255, 255)
                    borderPaint.color = if (note.isPinned) Color.rgb(218, 185, 110) else Color.rgb(225, 220, 210)

                    canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + noteCardHeight), 5f, 5f, bgPaint)
                    canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + noteCardHeight), 5f, 5f, borderPaint)

                    val noteTitleText = "${index + 1}. ${note.title}  [${note.tag}] ${if (note.isPinned) "★ مثبتة" else ""}"
                    val noteTitleLayout = createStaticLayout(noteTitleText, boldPaint, contentWidth - 24)
                    canvas.save()
                    canvas.translate(MARGIN + 12f, yPos + 8f)
                    noteTitleLayout.draw(canvas)
                    canvas.restore()

                    canvas.save()
                    canvas.translate(MARGIN + 12f, yPos + 26f)
                    noteContentLayout.draw(canvas)
                    canvas.restore()

                    val noteDateStr = "التاريخ: ${sdf.format(Date(note.updatedAt))}"
                    val noteDateLayout = createStaticLayout(noteDateStr, dimPaint, contentWidth - 24)
                    canvas.save()
                    canvas.translate(MARGIN + 12f, yPos + noteCardHeight - 16f)
                    noteDateLayout.draw(canvas)
                    canvas.restore()

                    yPos += noteCardHeight + 8f
                }
            }

            // 5. SCANNED DOCUMENTS & SUMMARIES
            if (scans.isNotEmpty()) {
                checkPageSpace(50f)
                val scanSecHeaderLayout = createStaticLayout("المستندات الممسوحة ضوئياً والملخصات (${scans.size})", subtitlePaint, contentWidth)
                canvas.save()
                canvas.translate(MARGIN, yPos)
                scanSecHeaderLayout.draw(canvas)
                canvas.restore()
                yPos += 24f

                scans.forEachIndexed { index, scan ->
                    val scanText = "${index + 1}. ${scan.title} • النوع: ${scan.docType} • مسجل بتاريخ: ${sdf.format(Date(scan.createdAt))}"
                    val scanNotes = if (!scan.ocrPreviewText.isNullOrBlank()) "نص الاستخراج (OCR): ${scan.ocrPreviewText}" else "المستند ممسوح ومحفوظ في الذاكرة المحلية."
                    val scanNotesLayout = createStaticLayout(scanNotes, bodyPaint, contentWidth - 24)
                    val scanCardHeight = 40f + scanNotesLayout.height

                    checkPageSpace(scanCardHeight + 8f)

                    bgPaint.color = Color.rgb(252, 252, 252)
                    borderPaint.color = Color.rgb(225, 220, 210)
                    canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + scanCardHeight), 5f, 5f, bgPaint)
                    canvas.drawRoundRect(RectF(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + scanCardHeight), 5f, 5f, borderPaint)

                    val scanTitleLayout = createStaticLayout(scanText, boldPaint, contentWidth - 24)
                    canvas.save()
                    canvas.translate(MARGIN + 12f, yPos + 8f)
                    scanTitleLayout.draw(canvas)
                    canvas.restore()

                    canvas.save()
                    canvas.translate(MARGIN + 12f, yPos + 24f)
                    scanNotesLayout.draw(canvas)
                    canvas.restore()

                    yPos += scanCardHeight + 8f
                }
            }

            // 6. PROCEDURAL DEADLINES & HEARINGS (if available)
            if (deadlines.isNotEmpty() || hearings.isNotEmpty()) {
                checkPageSpace(50f)
                val schedHeaderLayout = createStaticLayout("المواعيد الإجرائية والجلسات المرتبطة", subtitlePaint, contentWidth)
                canvas.save()
                canvas.translate(MARGIN, yPos)
                schedHeaderLayout.draw(canvas)
                canvas.restore()
                yPos += 24f

                deadlines.take(5).forEach { d ->
                    checkPageSpace(26f)
                    val dText = "• موعد إجرائي: ${d.triggerEvent} — الاستحقاق: ${d.computedDueDate} (${if (d.status == "confirmed") "مؤكد" else "مبدئي"})"
                    val dLayout = createStaticLayout(dText, bodyPaint, contentWidth - 10)
                    canvas.save()
                    canvas.translate(MARGIN + 6f, yPos)
                    dLayout.draw(canvas)
                    canvas.restore()
                    yPos += 20f
                }

                hearings.take(3).forEach { h ->
                    checkPageSpace(26f)
                    val hText = "• جلسة: ${h.sessionDate} ${h.sessionTime ?: ""} — القرار: ${h.adjournmentReason ?: h.outcome ?: "منعقدة"}"
                    val hLayout = createStaticLayout(hText, bodyPaint, contentWidth - 10)
                    canvas.save()
                    canvas.translate(MARGIN + 6f, yPos)
                    hLayout.draw(canvas)
                    canvas.restore()
                    yPos += 20f
                }
            }

            // Finish the last page
            drawFooter(canvas, currentPageNumber, dimPaint)
            document.finishPage(page)

            // Save to file in cache directory
            val outputDir = File(context.cacheDir, "hoyaam_reports").apply { mkdirs() }
            val sanitizedLabel = matter.matterLabel.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_\\-]"), "_")
            val fileName = "تقرير_${sanitizedLabel}_${System.currentTimeMillis()}.pdf"
            val pdfFile = File(outputDir, fileName)

            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }

            return pdfFile
        } catch (e: Exception) {
            Log.e("PdfExporter", "Error generating PDF", e)
            return null
        } finally {
            document.close()
        }
    }

    private fun createStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                paint,
                width,
                Layout.Alignment.ALIGN_NORMAL,
                1.2f,
                0f,
                true
            )
        }
    }

    private fun drawMiniHeader(canvas: Canvas, caseName: String, paint: TextPaint, dimPaint: TextPaint) {
        val headerText = "هيام للتقاضي • ملف قضية: $caseName"
        canvas.drawText(headerText, MARGIN, MARGIN, paint)
        val linePaint = Paint().apply {
            color = Color.rgb(220, 215, 205)
            strokeWidth = 0.8f
        }
        canvas.drawLine(MARGIN, MARGIN + 6f, PAGE_WIDTH - MARGIN, MARGIN + 6f, linePaint)
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, paint: TextPaint) {
        val footerY = PAGE_HEIGHT - MARGIN + 12f
        val linePaint = Paint().apply {
            color = Color.rgb(220, 215, 205)
            strokeWidth = 0.8f
        }
        canvas.drawLine(MARGIN, footerY - 14f, PAGE_WIDTH - MARGIN, footerY - 14f, linePaint)

        val footerText = "منظومة هيام لإدارة المكاتب القانونية والأرشفة الرقمية • صفحة $pageNumber"
        canvas.drawText(footerText, MARGIN, footerY, paint)
    }

    fun sharePdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "تقرير قضية: ${pdfFile.name}")
                putExtra(Intent.EXTRA_TEXT, "مرفق تقرير ملف القضية والمذكرات الصادر من منظومة هيام.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "مشاركة تقرير القضية عبر…"))
        } catch (e: Exception) {
            Log.e("PdfExporter", "Error sharing PDF", e)
            Toast.makeText(context, "تعذر فتح نافذة المشاركة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun printPdf(context: Context, pdfFile: File) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val printAdapter = object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: android.os.Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }
                        val pdi = PrintDocumentInfo.Builder(pdfFile.name)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build()
                        callback?.onLayoutFinished(pdi, true)
                    }

                    override fun onWrite(
                        pages: Array<out PageRange>?,
                        destination: ParcelFileDescriptor?,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        if (destination == null) return
                        try {
                            FileInputStream(pdfFile).use { input ->
                                FileOutputStream(destination.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.message)
                        }
                    }
                }
                printManager.print("تقرير قضية - ${pdfFile.name}", printAdapter, PrintAttributes.Builder().build())
            } else {
                viewPdf(context, pdfFile)
            }
        } catch (e: Exception) {
            Log.e("PdfExporter", "Error printing PDF", e)
            viewPdf(context, pdfFile)
        }
    }

    fun viewPdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PdfExporter", "Error viewing PDF", e)
            Toast.makeText(context, "تم حفظ الملف بنجاح في: ${pdfFile.name}", Toast.LENGTH_LONG).show()
        }
    }
}
