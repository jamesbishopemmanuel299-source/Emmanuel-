package com.example.util

import com.example.data.model.InvoiceDto
import com.example.data.model.QuestionType
import com.example.data.model.ResultDto

data class ParsedQuestionRow(
    val type: QuestionType,
    val prompt: String,
    val marks: Double,
    val options: List<String>,
    val correctAnswer: String,
    val markingGuide: String? = null
)

data class ParsedStudentImportRow(
    val fullName: String,
    val email: String,
    val admissionNumber: String,
    val department: String,
    val guardianContact: String?
)

object CsvExcelHelper {

    /**
     * Expected CSV/Excel columns for Questions:
     * type,prompt,marks,option_a,option_b,option_c,option_d,correct_answer,marking_guide
     * type values: mcq | true_false | fill_gap | theory
     * For mcq, correct_answer can be A/B/C/D or the exact option text.
     */
    fun parseQuestionsCsv(rawCsv: String): List<ParsedQuestionRow> {
        val lines = rawCsv.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val startIndex = if (lines.first().lowercase().contains("prompt")) 1 else 0
        val parsed = mutableListOf<ParsedQuestionRow>()

        for (i in startIndex until lines.size) {
            val cols = splitCsvLine(lines[i])
            if (cols.size < 3) continue
            val type = QuestionType.fromDb(cols.getOrNull(0)?.trim())
            val prompt = cols.getOrNull(1)?.trim().orEmpty()
            if (prompt.isBlank()) continue
            val marks = cols.getOrNull(2)?.trim()?.toDoubleOrNull() ?: 1.0
            val optA = cols.getOrNull(3)?.trim().orEmpty()
            val optB = cols.getOrNull(4)?.trim().orEmpty()
            val optC = cols.getOrNull(5)?.trim().orEmpty()
            val optD = cols.getOrNull(6)?.trim().orEmpty()
            val correct = cols.getOrNull(7)?.trim().orEmpty()
            val guide = cols.getOrNull(8)?.trim()?.takeIf { it.isNotBlank() }

            val options = listOf(optA, optB, optC, optD).filter { it.isNotBlank() }
            parsed += ParsedQuestionRow(
                type = type,
                prompt = prompt,
                marks = marks.coerceAtLeast(0.5),
                options = options,
                correctAnswer = correct,
                markingGuide = guide
            )
        }
        return parsed
    }

    /**
     * Expected CSV columns for Bulk Student Import:
     * full_name,email,admission_number,department,guardian_contact
     */
    fun parseStudentsCsv(rawCsv: String): List<ParsedStudentImportRow> {
        val lines = rawCsv.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val startIndex = if (lines.first().lowercase().contains("email")) 1 else 0
        val rows = mutableListOf<ParsedStudentImportRow>()
        for (i in startIndex until lines.size) {
            val cols = splitCsvLine(lines[i])
            if (cols.size < 3) continue
            val name = cols[0].trim()
            val email = cols[1].trim()
            val adm = cols[2].trim()
            if (name.isBlank() || email.isBlank() || adm.isBlank()) continue
            val dept = cols.getOrNull(3)?.trim()?.ifBlank { "General" } ?: "General"
            val phone = cols.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() }
            rows += ParsedStudentImportRow(name, email, adm, dept, phone)
        }
        return rows
    }

    fun exportResultsCsv(results: List<ResultDto>): String {
        val sb = StringBuilder()
        sb.appendLine("Admission No,Student Name,Subject,CA Score,Exam Score,Total,Grade,Remark,Position,Approved")
        for (r in results) {
            val adm = escapeCsv(r.students?.admissionNumber.orEmpty())
            val name = escapeCsv(r.students?.profiles?.fullName.orEmpty())
            val subject = escapeCsv(r.subjects?.name.orEmpty())
            sb.appendLine("$adm,$name,$subject,${r.caScore},${r.examScore},${r.totalScore},${r.grade},${escapeCsv(r.remark)},${r.classPosition ?: "-"},${r.isApproved}")
        }
        return sb.toString()
    }

    fun exportInvoicesCsv(invoices: List<InvoiceDto>): String {
        val sb = StringBuilder()
        sb.appendLine("Student,Admission No,Title,Fee Type,Amount NGN,Paid NGN,Status,Due Date")
        for (inv in invoices) {
            val name = escapeCsv(inv.students?.profiles?.fullName.orEmpty())
            val adm = escapeCsv(inv.students?.admissionNumber.orEmpty())
            val totalNgn = inv.amountKobo / 100.0
            val paidNgn = inv.amountPaidKobo / 100.0
            sb.appendLine("$name,$adm,${escapeCsv(inv.title)},${inv.feeType},$totalNgn,$paidNgn,${inv.status},${inv.dueDate}")
        }
        return sb.toString()
    }

    fun sampleQuestionsTemplate(): String = buildString {
        appendLine("type,prompt,marks,option_a,option_b,option_c,option_d,correct_answer,marking_guide")
        appendLine("mcq,\"What is the powerhouse of the cell?\",2,Nucleus,Mitochondrion,Ribosome,Golgi body,B,")
        appendLine("true_false,\"Nigeria gained independence in 1960.\",1,,,,,true,")
        appendLine("fill_gap,\"The chemical symbol for Gold is ____.\",2,,,,,Au,")
        appendLine("theory,\"Explain two causes of osmosis in plant cells.\",10,,,,,\"\",Mention semi-permeable membrane and water potential gradient.")
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                (ch == ',' || ch == '\t') && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result += current.toString()
        return result
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
