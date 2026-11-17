package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.PaymentDto
import com.example.data.model.ResultDto
import com.example.data.model.StudentDto
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {

    private val navyColor = Color.rgb(11, 23, 42)
    private val emeraldColor = Color.rgb(14, 138, 78)
    private val mutedColor = Color.rgb(71, 85, 105)
    private val borderColor = Color.rgb(203, 213, 225)
    private val tintGreen = Color.rgb(236, 253, 245)

    fun generateReportCardPdf(
        context: Context,
        schoolName: String,
        sessionLabel: String,
        student: StudentDto,
        results: List<ResultDto>
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val headerPaint = Paint().apply { color = navyColor; style = Paint.Style.FILL }
        val accentPaint = Paint().apply { color = emeraldColor; style = Paint.Style.FILL }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subHeaderPaint = Paint().apply { color = Color.rgb(167, 243, 208); textSize = 12f }
        val sectionPaint = Paint().apply {
            color = navyColor
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply { color = navyColor; textSize = 11f }
        val mutedPaint = Paint().apply { color = mutedColor; textSize = 10f }
        val linePaint = Paint().apply { color = borderColor; strokeWidth = 1f }
        val rowBgPaint = Paint().apply { color = tintGreen; style = Paint.Style.FILL }

        // Header banner
        canvas.drawRect(0f, 0f, 595f, 104f, headerPaint)
        canvas.drawRect(0f, 100f, 595f, 108f, accentPaint)
        canvas.drawText(schoolName.uppercase(), 36f, 44f, titlePaint)
        canvas.drawText("OFFICIAL TERMINAL REPORT CARD  |  $sessionLabel", 36f, 70f, subHeaderPaint)

        // Student Info Block
        var y = 136f
        canvas.drawText("STUDENT ACADEMIC PROFILE", 36f, y, sectionPaint)
        y += 22f
        val studentName = student.profiles?.fullName ?: "Student"
        canvas.drawText("Full Name: $studentName", 36f, y, bodyPaint)
        canvas.drawText("Admission No: ${student.admissionNumber}", 340f, y, bodyPaint)
        y += 18f
        canvas.drawText("Class: ${student.classes?.name ?: "Unassigned"}", 36f, y, bodyPaint)
        canvas.drawText("Department: ${student.department}", 340f, y, bodyPaint)
        y += 22f
        canvas.drawLine(36f, y, 559f, y, linePaint)

        // Table Header
        y += 22f
        canvas.drawRect(36f, y - 15f, 559f, y + 10f, rowBgPaint)
        val colBold = Paint(bodyPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        canvas.drawText("SUBJECT", 42f, y, colBold)
        canvas.drawText("CA (40)", 250f, y, colBold)
        canvas.drawText("EXAM (60)", 310f, y, colBold)
        canvas.drawText("TOTAL", 380f, y, colBold)
        canvas.drawText("GRADE", 435f, y, colBold)
        canvas.drawText("POS", 485f, y, colBold)
        canvas.drawText("REMARK", 518f, y, colBold)

        y += 24f
        var sumTotal = 0.0
        results.forEachIndexed { idx, res ->
            if (y > 760f) return@forEachIndexed
            if (idx % 2 == 1) {
                canvas.drawRect(36f, y - 13f, 559f, y + 7f, Paint().apply {
                    color = Color.rgb(248, 250, 252)
                    style = Paint.Style.FILL
                })
            }
            val subjName = (res.subjects?.name ?: "Subject").take(28)
            canvas.drawText(subjName, 42f, y, bodyPaint)
            canvas.drawText("${res.caScore}", 255f, y, bodyPaint)
            canvas.drawText("${res.examScore}", 320f, y, bodyPaint)
            canvas.drawText("${res.totalScore}", 385f, y, colBold)
            canvas.drawText(res.grade, 442f, y, colBold)
            canvas.drawText(ExamEngine.formatOrdinal(res.classPosition), 486f, y, bodyPaint)
            canvas.drawText(res.remark.take(10), 518f, y, mutedPaint)
            canvas.drawLine(36f, y + 7f, 559f, y + 7f, linePaint)
            sumTotal += res.totalScore
            y += 22f
        }

        val average = if (results.isNotEmpty()) sumTotal / results.size else 0.0
        y += 18f
        canvas.drawText("Cumulative Average: ${"%.2f".format(average)}%", 36f, y, sectionPaint)
        y += 20f
        canvas.drawText("Overall Grade: ${ExamEngine.calculateGrade(average).grade} (${ExamEngine.calculateGrade(average).remark})", 36f, y, bodyPaint)

        canvas.drawText("Verified & Approved by Green Light Academy Examination Office", 36f, 804f, mutedPaint)
        doc.finishPage(page)

        val outFile = exportFile(context, "GLA_Report_${student.admissionNumber}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun generateStudentIdCardPdf(
        context: Context,
        schoolName: String,
        sessionLabel: String,
        student: StudentDto
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(420, 270, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        // Card Outer Frame
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(0f, 0f, 420f, 72f, Paint().apply { color = navyColor })
        canvas.drawRect(0f, 68f, 420f, 75f, Paint().apply { color = emeraldColor })

        canvas.drawText(schoolName.uppercase(), 20f, 34f, Paint().apply {
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        canvas.drawText("OFFICIAL STUDENT IDENTITY CARD", 20f, 54f, Paint().apply {
            color = Color.rgb(167, 243, 208)
            textSize = 10f
        })

        // Avatar placeholder frame
        canvas.drawRect(22f, 94f, 114f, 198f, Paint().apply {
            color = tintGreen
            style = Paint.Style.FILL
        })
        canvas.drawRect(22f, 94f, 114f, 198f, Paint().apply {
            color = emeraldColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
        })
        val initials = (student.profiles?.fullName ?: "ST")
            .split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
        canvas.drawText(initials, 48f, 156f, Paint().apply {
            color = navyColor
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })

        val labelPaint = Paint().apply { color = mutedColor; textSize = 9.5f }
        val valPaint = Paint().apply {
            color = navyColor
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("STUDENT NAME", 134f, 106f, labelPaint)
        canvas.drawText(student.profiles?.fullName ?: "Student", 134f, 122f, valPaint)

        canvas.drawText("ADMISSION NUMBER", 134f, 144f, labelPaint)
        canvas.drawText(student.admissionNumber, 134f, 160f, valPaint)

        canvas.drawText("CLASS & DEPARTMENT", 134f, 182f, labelPaint)
        canvas.drawText("${student.classes?.name ?: "Assigned"} • ${student.department}", 134f, 198f, valPaint)

        canvas.drawRect(0f, 232f, 420f, 270f, Paint().apply { color = tintGreen })
        canvas.drawText("SESSION: $sessionLabel   |   STATUS: ${student.admissionStatus.uppercase()}", 20f, 255f, Paint().apply {
            color = emeraldColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })

        doc.finishPage(page)
        val outFile = exportFile(context, "GLA_ID_${student.admissionNumber}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun generatePaymentReceiptPdf(
        context: Context,
        schoolName: String,
        studentName: String,
        admissionNumber: String,
        payment: PaymentDto,
        invoiceTitle: String
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(500, 420, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawRect(0f, 0f, 500f, 84f, Paint().apply { color = navyColor })
        canvas.drawRect(0f, 80f, 500f, 86f, Paint().apply { color = emeraldColor })
        canvas.drawText(schoolName.uppercase(), 28f, 38f, Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        canvas.drawText("PAYSTACK VERIFIED DIGITAL RECEIPT", 28f, 60f, Paint().apply {
            color = Color.rgb(167, 243, 208)
            textSize = 11f
        })

        val labelPaint = Paint().apply { color = mutedColor; textSize = 11f }
        val valPaint = Paint().apply {
            color = navyColor
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var y = 126f
        val rows = listOf(
            "Student Name" to studentName,
            "Admission No" to admissionNumber,
            "Fee Item" to invoiceTitle,
            "Amount Paid" to ExamEngine.formatNairaFromKobo(payment.amountKobo),
            "Paystack Reference" to payment.reference,
            "Payment Channel" to (payment.channel ?: "Paystack"),
            "Verification Status" to payment.status.uppercase(),
            "Paid At" to (payment.paidAt ?: payment.createdAt ?: "-")
        )
        rows.forEach { (k, v) ->
            canvas.drawText(k, 28f, y, labelPaint)
            canvas.drawText(v, 190f, y, valPaint)
            canvas.drawLine(28f, y + 10f, 472f, y + 10f, Paint().apply { color = borderColor })
            y += 32f
        }

        doc.finishPage(page)
        val outFile = exportFile(context, "GLA_Receipt_${payment.reference}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun shareOrOpenFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open or Share ${file.name}"))
    }

    fun saveTextExport(context: Context, fileName: String, content: String): File {
        val outFile = exportFile(context, fileName)
        outFile.writeText(content)
        return outFile
    }

    private fun exportFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
    }
}
