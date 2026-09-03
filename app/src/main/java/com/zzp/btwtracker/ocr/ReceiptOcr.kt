package com.zzp.btwtracker.ocr

import android.graphics.Bitmap
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ReceiptScanResult(
    val rawText: String,
    val merchantName: String?,
    val totalAmount: BigDecimal?,
    val vatAmount: BigDecimal?,
    val kvkNumber: String?,
    val date: LocalDate?,
    val confidence: Int
)

object ReceiptParser {
    private val kvkRegex = Regex("\\b[0-9]{8}\\b")
    private val dateRegex = Regex("\\b(0?[1-9]|[12][0-9]|3[01])[-/](0?[1-9]|1[0-2])[-/](20[0-9]{2})\\b")
    private val money = "(?:€\\s*)?([0-9]{1,6}(?:[.,][0-9]{2}))"
    private val totalRegex = Regex("(?i)(?:totaal|total|te\\s*betalen|bedrag\\s*incl\\.?\\s*btw|incl\\.?\\s*btw)[^0-9€]{0,20}$money")
    private val vatRegex = Regex("(?i)(?:btw|vat)(?:\\s*(?:21|9)\\s*%)?[^0-9€]{0,20}$money")

    fun parse(text: String): ReceiptScanResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val total = lines.asReversed().firstNotNullOfOrNull { line ->
            totalRegex.find(line)?.groupValues?.getOrNull(1)?.toMoney()
        } ?: findLargestMoney(lines)

        val vat = lines.firstNotNullOfOrNull { line ->
            vatRegex.find(line)?.groupValues?.getOrNull(1)?.toMoney()
        }

        val date = dateRegex.find(text)?.value?.let(::parseDate)
        return ReceiptScanResult(
            rawText = text,
            merchantName = lines.firstOrNull { it.length in 3..50 && it.any(Char::isLetter) },
            totalAmount = total,
            vatAmount = vat,
            kvkNumber = kvkRegex.find(text)?.value,
            date = date,
            confidence = listOfNotNull(total, vat, date, kvkRegex.find(text)?.value).size * 25
        )
    }

    private fun findLargestMoney(lines: List<String>): BigDecimal? = lines
        .flatMap { Regex(money).findAll(it).mapNotNull { m -> m.groupValues.getOrNull(1)?.toMoney() } }
        .maxOrNull()

    private fun String.toMoney(): BigDecimal? = runCatching {
        (if (contains(',')) replace(".", "").replace(',', '.') else this).toBigDecimal().setScale(2)
    }.getOrNull()

    private fun parseDate(value: String): LocalDate? {
        val normalized = value.replace('/', '-')
        return runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern("d-M-yyyy")) }.getOrNull()
    }
}

class ReceiptOcrScanner {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scan(bitmap: Bitmap): ReceiptScanResult = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(ReceiptParser.parse(result.text))
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }

    suspend fun scan(context: Context, uri: Uri): ReceiptScanResult = suspendCancellableCoroutine { continuation ->
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse { continuation.resumeWithException(it); return@suspendCancellableCoroutine }
        recognizer.process(image).addOnSuccessListener { if (continuation.isActive) continuation.resume(ReceiptParser.parse(it.text)) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }

    fun close() = recognizer.close()
}
