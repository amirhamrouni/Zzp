package com.zzp.btwtracker

import com.zzp.btwtracker.ocr.ReceiptParser
import com.zzp.btwtracker.tax.BelastingdienstAggregator
import com.zzp.btwtracker.tax.DutchVatEngine
import com.zzp.btwtracker.tax.Quarter
import com.zzp.btwtracker.tax.TransactionDraft
import com.zzp.btwtracker.tax.TransactionType
import com.zzp.btwtracker.tax.VatTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import com.zzp.btwtracker.coach.ZzpCoach

class CoreLogicTest {
    @Test
    fun domesticHighRateCalculatesInclusiveVat() {
        val result = DutchVatEngine.calculate(
            TransactionDraft(TransactionType.INCOME, "Consultancy", BigDecimal("121.00"), 21, VatTreatment.DOMESTIC, LocalDate.of(2026, 1, 10))
        )
        assertEquals(BigDecimal("100.00"), result.net)
        assertEquals(BigDecimal("21.00"), result.vat)
        assertEquals("1a", result.taxBox.code)
    }

    @Test
    fun euClientReverseChargeMapsTo3b() {
        val result = DutchVatEngine.calculate(
            TransactionDraft(TransactionType.INCOME, "EU service", BigDecimal("1000.00"), 0, VatTreatment.EU_REVERSE_CHARGE, LocalDate.of(2026, 2, 1))
        )
        assertEquals(BigDecimal("0.00"), result.vat)
        assertEquals("3b", result.taxBox.code)
    }

    @Test
    fun euPurchaseSelfAssessesVatAndOffsetsInput() {
        val entity = DutchVatEngine.toEntity(
            TransactionDraft(TransactionType.EXPENSE, "Software EU", BigDecimal("100.00"), 21, VatTreatment.EU_PURCHASE, LocalDate.of(2026, 3, 1))
        )
        val report = BelastingdienstAggregator.aggregate(Quarter(2026, 1), listOf(entity))
        assertEquals(10000L, report.box4bBaseCents)
        assertEquals(2100L, report.box4bVatCents)
        assertEquals(2100L, report.inputVat5bCents)
        assertEquals(0L, report.payableCents)
    }

    @Test
    fun receiptParserFindsDutchFields() {
        val parsed = ReceiptParser.parse("""
            Voorbeeld BV
            KvK 12345678
            Datum 03-09-2026
            BTW 21% 2,10
            Totaal € 12,10
        """.trimIndent())
        assertEquals("12345678", parsed.kvkNumber)
        assertEquals(LocalDate.of(2026, 9, 3), parsed.date)
        assertEquals(BigDecimal("12.10"), parsed.totalAmount)
        assertEquals(BigDecimal("2.10"), parsed.vatAmount)
        assertEquals("Voorbeeld BV", parsed.merchantName)
        assertEquals(100, parsed.confidence)
        assertNotNull(parsed.rawText)
    }

    @Test fun receiptParserAcceptsDotDecimals() {
        val parsed = ReceiptParser.parse("Shop BV\nBTW 2.10\nTotaal 12.10")
        assertEquals(BigDecimal("12.10"), parsed.totalAmount)
        assertEquals(BigDecimal("2.10"), parsed.vatAmount)
    }

    @Test fun coachWarnsNearKorLimit() {
        val result = ZzpCoach.build(1_700_000, 600 * 60, 42_000, emptyList(), emptyList())
        assertEquals(true, result.any { it.title.contains("KOR") })
        assertEquals(true, result.any { it.title.contains("Urencriterium") })
    }
}
