package com.zzp.btwtracker.tax

import com.zzp.btwtracker.data.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class TransactionType { INCOME, EXPENSE }
enum class VatTreatment { DOMESTIC, EU_REVERSE_CHARGE, EU_PURCHASE, NON_EU_PURCHASE, EXEMPT }
enum class TaxBox(val code: String, val label: String) {
    BOX_1A("1a", "Leveringen/diensten hoog tarief"),
    BOX_1B("1b", "Leveringen/diensten laag tarief"),
    BOX_1E("1e", "0% of niet bij u belast"),
    BOX_3B("3b", "Leveringen/diensten binnen EU"),
    BOX_4A("4a", "Prestaties uit landen buiten EU"),
    BOX_4B("4b", "Prestaties uit landen binnen EU"),
    BOX_5B("5b", "Voorbelasting"),
    EXEMPT("vrijgesteld", "Vrijgestelde omzet")
}

data class TransactionDraft(
    val type: TransactionType,
    val description: String,
    val grossAmount: BigDecimal,
    val vatRate: Int,
    val treatment: VatTreatment,
    val date: LocalDate,
    val counterpartyName: String? = null,
    val kvkNumber: String? = null,
    val vatNumber: String? = null,
    val countryCode: String = "NL",
    val explicitVatAmount: BigDecimal? = null,
    val receiptUri: String? = null
)

data class VatBreakdown(val net: BigDecimal, val vat: BigDecimal, val gross: BigDecimal, val taxBox: TaxBox)

object DutchVatEngine {
    private val hundred = BigDecimal("100")

    fun calculate(draft: TransactionDraft): VatBreakdown {
        require(draft.grossAmount >= BigDecimal.ZERO) { "Bedrag mag niet negatief zijn" }
        require(draft.vatRate in setOf(0, 9, 21)) { "Ondersteunde btw-tarieven: 0%, 9%, 21%" }

        val charged = draft.grossAmount.setScale(2, RoundingMode.HALF_UP)
        val foreignReverseChargePurchase = draft.type == TransactionType.EXPENSE &&
            draft.treatment in setOf(VatTreatment.EU_PURCHASE, VatTreatment.NON_EU_PURCHASE)

        val vat = when {
            draft.explicitVatAmount != null -> draft.explicitVatAmount.setScale(2, RoundingMode.HALF_UP)
            draft.treatment == VatTreatment.EU_REVERSE_CHARGE -> BigDecimal.ZERO.setScale(2)
            draft.treatment == VatTreatment.EXEMPT || draft.vatRate == 0 -> BigDecimal.ZERO.setScale(2)
            foreignReverseChargePurchase -> charged.multiply(BigDecimal(draft.vatRate)).divide(hundred, 2, RoundingMode.HALF_UP)
            else -> charged.multiply(BigDecimal(draft.vatRate)).divide(hundred.add(BigDecimal(draft.vatRate)), 2, RoundingMode.HALF_UP)
        }

        // For foreign reverse-charge purchases, the supplier amount is the taxable base; Dutch VAT is self-assessed.
        val net = if (foreignReverseChargePurchase) charged else charged.subtract(vat).setScale(2, RoundingMode.HALF_UP)
        val gross = if (foreignReverseChargePurchase) charged else charged
        return VatBreakdown(net, vat, gross, mapBox(draft))
    }

    fun mapBox(draft: TransactionDraft): TaxBox = when (draft.type) {
        TransactionType.INCOME -> when (draft.treatment) {
            VatTreatment.EU_REVERSE_CHARGE -> TaxBox.BOX_3B
            VatTreatment.EXEMPT -> TaxBox.EXEMPT
            else -> when (draft.vatRate) {
                21 -> TaxBox.BOX_1A
                9 -> TaxBox.BOX_1B
                else -> TaxBox.BOX_1E
            }
        }
        TransactionType.EXPENSE -> when (draft.treatment) {
            VatTreatment.EU_PURCHASE -> TaxBox.BOX_4B
            VatTreatment.NON_EU_PURCHASE -> TaxBox.BOX_4A
            VatTreatment.EXEMPT -> TaxBox.EXEMPT
            else -> TaxBox.BOX_5B
        }
    }

    fun toEntity(draft: TransactionDraft): TransactionEntity {
        val b = calculate(draft)
        return TransactionEntity(
            type = draft.type.name,
            description = draft.description.ifBlank { if (draft.type == TransactionType.INCOME) "Omzet" else "Zakelijke kosten" },
            netCents = b.net.movePointRight(2).longValueExact(),
            vatCents = b.vat.movePointRight(2).longValueExact(),
            grossCents = b.gross.movePointRight(2).longValueExact(),
            vatRate = draft.vatRate,
            vatTreatment = draft.treatment.name,
            taxBox = b.taxBox.code,
            dateEpochDay = draft.date.toEpochDay(),
            counterpartyName = draft.counterpartyName,
            kvkNumber = draft.kvkNumber,
            vatNumber = draft.vatNumber,
            countryCode = draft.countryCode.uppercase(),
            receiptUri = draft.receiptUri
        )
    }
}

data class Quarter(val year: Int, val number: Int) {
    init { require(number in 1..4) }
    val start: LocalDate get() = LocalDate.of(year, (number - 1) * 3 + 1, 1)
    val end: LocalDate get() = start.plusMonths(3).minusDays(1)
    override fun toString() = "Q$number $year"
    companion object { fun from(date: LocalDate) = Quarter(date.year, ((date.monthValue - 1) / 3) + 1) }
}

data class BelastingdienstReport(
    val quarter: Quarter,
    val box1aTurnoverCents: Long, val box1aVatCents: Long,
    val box1bTurnoverCents: Long, val box1bVatCents: Long,
    val box1eTurnoverCents: Long,
    val box3bTurnoverCents: Long,
    val box4aBaseCents: Long, val box4aVatCents: Long,
    val box4bBaseCents: Long, val box4bVatCents: Long,
    val inputVat5bCents: Long,
    val vatDue5aCents: Long,
    val payableCents: Long
)

object BelastingdienstAggregator {
    fun aggregate(quarter: Quarter, tx: List<TransactionEntity>): BelastingdienstReport {
        fun net(box: String) = tx.filter { it.taxBox == box }.sumOf { it.netCents }
        fun vat(box: String) = tx.filter { it.taxBox == box }.sumOf { it.vatCents }
        val due = vat("1a") + vat("1b") + vat("4a") + vat("4b")
        // Reverse-charge VAT from 4a/4b is also deductible in 5b when fully attributable to taxable business activity.
        val input = vat("5b") + vat("4a") + vat("4b")
        return BelastingdienstReport(
            quarter, net("1a"), vat("1a"), net("1b"), vat("1b"), net("1e"), net("3b"),
            net("4a"), vat("4a"), net("4b"), vat("4b"), input, due, due - input
        )
    }
}
