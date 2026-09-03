package com.zzp.btwtracker.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.zzp.btwtracker.tax.BelastingdienstReport
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.Locale

object QuarterExportService {
    private val money = NumberFormat.getCurrencyInstance(Locale("nl", "NL"))

    fun csv(report: BelastingdienstReport): ByteArray {
        val rows = listOf(
            listOf("Tijdvak", report.quarter.toString()),
            listOf("Rubriek", "Omschrijving", "Omzet/grondslag", "BTW"),
            listOf("1a", "Leveringen/diensten hoog tarief", cents(report.box1aTurnoverCents), cents(report.box1aVatCents)),
            listOf("1b", "Leveringen/diensten laag tarief", cents(report.box1bTurnoverCents), cents(report.box1bVatCents)),
            listOf("1e", "0% of niet bij u belast", cents(report.box1eTurnoverCents), ""),
            listOf("3b", "Leveringen/diensten binnen EU", cents(report.box3bTurnoverCents), ""),
            listOf("4a", "Prestaties uit landen buiten EU", cents(report.box4aBaseCents), cents(report.box4aVatCents)),
            listOf("4b", "Prestaties uit landen binnen EU", cents(report.box4bBaseCents), cents(report.box4bVatCents)),
            listOf("5a", "Verschuldigde btw", "", cents(report.vatDue5aCents)),
            listOf("5b", "Voorbelasting", "", cents(report.inputVat5bCents)),
            listOf("Totaal", if (report.payableCents >= 0) "Te betalen" else "Terug te vragen", "", cents(kotlin.math.abs(report.payableCents)))
        )
        return rows.joinToString("\r\n") { row -> row.joinToString(";") { csvEscape(it) } }
            .plus("\r\n")
            .toByteArray(Charsets.UTF_8)
    }

    fun pdf(report: BelastingdienstReport): ByteArray {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val title = Paint().apply { textSize = 22f; isFakeBoldText = true }
        val heading = Paint().apply { textSize = 13f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 11f }

        var y = 55f
        canvas.drawText("ZZP BTW Tracker", 40f, y, title)
        y += 30f
        canvas.drawText("BTW-aangifte overzicht ${report.quarter}", 40f, y, heading)
        y += 28f
        canvas.drawText("Rubriek", 40f, y, heading)
        canvas.drawText("Omschrijving", 100f, y, heading)
        canvas.drawText("Omzet/grondslag", 355f, y, heading)
        canvas.drawText("BTW", 485f, y, heading)
        y += 18f

        val lines = listOf(
            arrayOf("1a", "Hoog tarief", cents(report.box1aTurnoverCents), cents(report.box1aVatCents)),
            arrayOf("1b", "Laag tarief", cents(report.box1bTurnoverCents), cents(report.box1bVatCents)),
            arrayOf("1e", "0% / niet bij u belast", cents(report.box1eTurnoverCents), ""),
            arrayOf("3b", "Leveringen/diensten binnen EU", cents(report.box3bTurnoverCents), ""),
            arrayOf("4a", "Uit landen buiten EU", cents(report.box4aBaseCents), cents(report.box4aVatCents)),
            arrayOf("4b", "Uit landen binnen EU", cents(report.box4bBaseCents), cents(report.box4bVatCents)),
            arrayOf("5a", "Verschuldigde btw", "", cents(report.vatDue5aCents)),
            arrayOf("5b", "Voorbelasting", "", cents(report.inputVat5bCents))
        )
        lines.forEach { line ->
            canvas.drawText(line[0], 40f, y, body)
            canvas.drawText(line[1], 100f, y, body)
            canvas.drawText(line[2], 355f, y, body)
            canvas.drawText(line[3], 485f, y, body)
            y += 22f
        }
        y += 15f
        canvas.drawText(if (report.payableCents >= 0) "Te betalen" else "Terug te vragen", 355f, y, heading)
        canvas.drawText(money.format(kotlin.math.abs(report.payableCents) / 100.0), 485f, y, heading)
        y += 40f
        canvas.drawText("Controleer dit overzicht altijd met uw administratie voordat u aangifte doet.", 40f, y, body)

        document.finishPage(page)
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun cents(value: Long): String = "%.2f".format(Locale.US, value / 100.0).replace('.', ',')
    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
