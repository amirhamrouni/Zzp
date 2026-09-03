package com.zzp.btwtracker.coach

import com.zzp.btwtracker.data.InvoiceEntity
import com.zzp.btwtracker.data.ReceiptInboxEntity

data class CoachInsight(val level: String, val title: String, val detail: String)

object ZzpCoach {
    const val KOR_LIMIT_CENTS = 2_000_000L
    const val HOURS_TARGET_MINUTES = 1_225 * 60

    fun build(
        annualIncomeCents: Long,
        annualMinutes: Int,
        vatReserveCents: Long,
        invoices: List<InvoiceEntity>,
        receipts: List<ReceiptInboxEntity>
    ): List<CoachInsight> = buildList {
        if (vatReserveCents > 0) add(CoachInsight("MONEY", "Zet btw apart", "Reserveer € %.2f voor de komende aangifte.".format(vatReserveCents / 100.0)))
        val korPercent = (annualIncomeCents * 100 / KOR_LIMIT_CENTS).coerceIn(0, 999)
        if (korPercent >= 80) add(CoachInsight("WARNING", "KOR-grens komt dichtbij", "$korPercent% van €20.000 jaaromzet bereikt."))
        val hours = annualMinutes / 60
        if (hours < 1_225) add(CoachInsight("INFO", "Urencriterium", "$hours van 1.225 geregistreerde uren; nog ${1_225 - hours} te gaan."))
        val overdue = invoices.count { it.status == "OVERDUE" }
        if (overdue > 0) add(CoachInsight("WARNING", "$overdue facturen te laat", "Volg deze klanten op om je cashflow gezond te houden."))
        if (receipts.isNotEmpty()) add(CoachInsight("ACTION", "${receipts.size} bonnetjes wachten", "Controleer de OCR-gegevens en boek ze in."))
        if (isEmpty()) add(CoachInsight("OK", "Alles bijgewerkt", "Er zijn nu geen urgente administratieve acties."))
    }
}
