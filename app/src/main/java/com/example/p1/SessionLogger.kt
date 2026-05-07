package com.example.p1

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * CSV session logger for pilot-testing the adaptive numeracy app.
 *
 * Log types:
 *   SETTINGS   — session configuration
 *   MASTERY    — when a concept is mastered
 *   ZPD-UPDATE — when new concepts enter the ZPD
 *   PRACTICE   — each practice (learning) question
 *   DETECTION  — each boundary-detection (assessment) question
 *   K-BOUNDARY — knowledge boundary after assessment
 */
class SessionLogger(context: Context) {

    private val TAG = "SessionLogger"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // Store logs in app-specific external storage (no permissions needed)
    private val logDir = File(context.getExternalFilesDir(null), "session_logs").also { it.mkdirs() }
    private var writer: FileWriter? = null
    private var currentFile: File? = null

    fun startSession() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            currentFile = File(logDir, "session_$timestamp.csv")
            writer = FileWriter(currentFile, true)
            // Write CSV header
            writer?.appendLine("log-type,timestamp,field-1,field-2,field-3,field-4,field-5,field-6,field-7")
            writer?.flush()
            Log.i(TAG, "Session log started: ${currentFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session log", e)
        }
    }

    private fun now(): String = dateFormat.format(Date())

    private fun writeLine(vararg fields: String) {
        try {
            // Pad to 9 fields (log-type + timestamp + 7 fields)
            val padded = fields.toMutableList()
            while (padded.size < 9) padded.add("")
            val line = padded.joinToString(",") { escapeCSV(it) }
            writer?.appendLine(line)
            writer?.flush()
            Log.d(TAG, "CSV: $line")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log line", e)
        }
    }

    private fun escapeCSV(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }

    // ─── Log type: SETTINGS ──────────────────────────────────────────────────
    fun logSettings(language: String, operation: String, maxDigit: Int) {
        writeLine("SETTINGS", now(), language, operation, maxDigit.toString(), "", "", "", "")
    }

    // ─── Log type: MASTERY ───────────────────────────────────────────────────
    fun logMastery(questionNo: Int, concept: String, correctnessRecord: List<Boolean>) {
        val record = correctnessRecord.joinToString("|") { if (it) "C" else "NC" }
        writeLine("MASTERY", now(), questionNo.toString(), concept, record, "", "", "", "")
    }

    // ─── Log type: ZPD-UPDATE ────────────────────────────────────────────────
    fun logZpdUpdate(conceptsAdded: List<String>) {
        val concepts = conceptsAdded.joinToString("|")
        writeLine("ZPD-UPDATE", now(), concepts, "", "", "", "", "", "")
    }

    // ─── Log type: PRACTICE ──────────────────────────────────────────────────
    fun logPractice(
        questionNo: Int,
        concept: String,
        question: String,
        correctAnswer: Int,
        answerSelected: Int,
        correct: Boolean,
        misconception: String
    ) {
        writeLine(
            "PRACTICE", now(),
            questionNo.toString(), concept, question,
            correctAnswer.toString(), answerSelected.toString(),
            if (correct) "C" else "NC", misconception
        )
    }

    // ─── Log type: DETECTION ─────────────────────────────────────────────────
    fun logDetection(
        questionNo: Int,
        concept: String,
        question: String,
        correctAnswer: Int,
        answerSelected: Int,
        correct: Boolean,
        misconception: String
    ) {
        writeLine(
            "DETECTION", now(),
            questionNo.toString(), concept, question,
            correctAnswer.toString(), answerSelected.toString(),
            if (correct) "C" else "NC", misconception
        )
    }

    // ─── Log type: K-BOUNDARY ────────────────────────────────────────────────
    fun logKBoundary(zpd: List<String>) {
        val zpdStr = zpd.joinToString("|")
        writeLine("K-BOUNDARY", now(), zpdStr, "", "", "", "", "", "")
    }

    fun endSession() {
        try {
            writer?.flush()
            writer?.close()
            writer = null
            Log.i(TAG, "Session log ended: ${currentFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end session log", e)
        }
    }
}
