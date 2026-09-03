package com.zzp.btwtracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zzp.btwtracker.data.CustomerEntity
import com.zzp.btwtracker.data.InvoiceEntity
import com.zzp.btwtracker.data.ReceiptInboxEntity
import com.zzp.btwtracker.data.TransactionEntity
import com.zzp.btwtracker.data.ZzpDatabase
import com.zzp.btwtracker.data.WorkSessionEntity
import com.zzp.btwtracker.data.BusinessTripEntity
import com.zzp.btwtracker.tax.BelastingdienstAggregator
import com.zzp.btwtracker.tax.BelastingdienstReport
import com.zzp.btwtracker.tax.DutchVatEngine
import com.zzp.btwtracker.tax.Quarter
import com.zzp.btwtracker.tax.TransactionDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ZzpDatabase.get(application)
    private val dao = db.transactionDao()
    private val customerDao = db.customerDao()
    private val invoiceDao = db.invoiceDao()
    private val receiptInboxDao = db.receiptInboxDao()
    private val workSessionDao = db.workSessionDao()
    private val businessTripDao = db.businessTripDao()

    val transactions: StateFlow<List<TransactionEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = customerDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val invoices: StateFlow<List<InvoiceEntity>> = invoiceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingReceipts: StateFlow<List<ReceiptInboxEntity>> = receiptInboxDao.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workSessions = workSessionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val businessTrips = businessTripDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addWorkSession(project: String, minutes: Int, description: String, date: LocalDate, onDone: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch { onDone(runCatching {
            require(project.isNotBlank()) { "Project is verplicht" }; require(minutes in 1..1440) { "Voer geldige minuten in" }
            workSessionDao.insert(WorkSessionEntity(dateEpochDay = date.toEpochDay(), minutes = minutes, project = project.trim(), description = description.trim()))
        }) }
    }

    fun addBusinessTrip(origin: String, destination: String, purpose: String, kilometers: BigDecimal, date: LocalDate, onDone: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch { onDone(runCatching {
            require(origin.isNotBlank() && destination.isNotBlank() && purpose.isNotBlank()) { "Vul alle velden in" }
            require(kilometers > BigDecimal.ZERO) { "Afstand moet groter zijn dan 0" }
            businessTripDao.insert(BusinessTripEntity(dateEpochDay = date.toEpochDay(), origin = origin.trim(), destination = destination.trim(), purpose = purpose.trim(), kilometersTimes10 = kilometers.movePointRight(1).setScale(0, RoundingMode.HALF_UP).intValueExact()))
        }) }
    }

    private val _quarter = MutableStateFlow(Quarter.from(LocalDate.now()))
    val quarter: StateFlow<Quarter> = _quarter

    private val _report = MutableStateFlow(BelastingdienstAggregator.aggregate(_quarter.value, emptyList()))
    val report: StateFlow<BelastingdienstReport> = _report

    init { refreshReport() }

    fun save(draft: TransactionDraft, onDone: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { dao.insert(DutchVatEngine.toEntity(draft)) }
            result.onSuccess { refreshReport() }
            onDone(result)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
            refreshReport()
        }
    }

    fun addCustomer(
        name: String,
        email: String?,
        address: String?,
        postalCode: String?,
        city: String?,
        countryCode: String,
        kvkNumber: String?,
        vatNumber: String?,
        iban: String?,
        onDone: (Result<Long>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = runCatching {
                require(name.isNotBlank()) { "Naam is verplicht" }
                customerDao.insert(
                    CustomerEntity(
                        name = name.trim(),
                        email = email?.trim()?.ifBlank { null },
                        address = address?.trim()?.ifBlank { null },
                        postalCode = postalCode?.trim()?.ifBlank { null },
                        city = city?.trim()?.ifBlank { null },
                        countryCode = countryCode.trim().uppercase().ifBlank { "NL" },
                        kvkNumber = kvkNumber?.filter(Char::isDigit)?.ifBlank { null },
                        vatNumber = vatNumber?.trim()?.ifBlank { null },
                        iban = iban?.replace(" ", "")?.uppercase()?.ifBlank { null }
                    )
                )
            }
            onDone(result)
        }
    }

    fun createInvoice(
        customer: CustomerEntity,
        description: String,
        grossAmount: BigDecimal,
        vatRate: Int,
        issueDate: LocalDate,
        dueDays: Long = 14,
        onDone: (Result<Long>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = runCatching {
                require(description.isNotBlank()) { "Omschrijving is verplicht" }
                require(grossAmount > BigDecimal.ZERO) { "Bedrag moet groter zijn dan 0" }
                require(vatRate in setOf(0, 9, 21)) { "Ongeldig btw-tarief" }

                val grossCents = grossAmount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
                val divisor = BigDecimal(100 + vatRate)
                val netCents = if (vatRate == 0) grossCents else
                    BigDecimal(grossCents).multiply(BigDecimal(100)).divide(divisor, 0, RoundingMode.HALF_UP).longValueExact()
                val vatCents = grossCents - netCents
                val year = issueDate.year
                val sequence = invoiceDao.countForYear("$year-") + 1
                val invoiceNumber = "%d-%03d".format(year, sequence)

                invoiceDao.insert(
                    InvoiceEntity(
                        invoiceNumber = invoiceNumber,
                        customerId = customer.id,
                        customerName = customer.name,
                        customerEmail = customer.email,
                        issueDateEpochDay = issueDate.toEpochDay(),
                        dueDateEpochDay = issueDate.plusDays(dueDays).toEpochDay(),
                        description = description.trim(),
                        netCents = netCents,
                        vatRate = vatRate,
                        vatCents = vatCents,
                        grossCents = grossCents,
                        status = "OPEN"
                    )
                )
            }
            onDone(result)
        }
    }

    fun markInvoicePaid(id: Long) {
        viewModelScope.launch {
            invoiceDao.updateStatus(id, "PAID", LocalDate.now().toEpochDay())
        }
    }

    fun refreshOverdueInvoices() {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            invoices.value.filter { it.status == "OPEN" && it.dueDateEpochDay < today }
                .forEach { invoiceDao.updateStatus(it.id, "OVERDUE") }
        }
    }

    fun addReceiptToInbox(item: ReceiptInboxEntity, onDone: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch { onDone(runCatching { receiptInboxDao.insert(item) }) }
    }

    fun dismissReceipt(id: Long) {
        viewModelScope.launch { receiptInboxDao.updateStatus(id, "DISMISSED") }
    }

    fun markReceiptBooked(id: Long) {
        viewModelScope.launch { receiptInboxDao.updateStatus(id, "BOOKED") }
    }

    fun moveQuarter(delta: Int) {
        val current = _quarter.value
        val absolute = current.year * 4 + (current.number - 1) + delta
        val year = Math.floorDiv(absolute, 4)
        val number = Math.floorMod(absolute, 4) + 1
        _quarter.value = Quarter(year, number)
        refreshReport()
    }

    fun refreshReport() {
        viewModelScope.launch {
            val q = _quarter.value
            val tx = dao.forPeriod(q.start.toEpochDay(), q.end.toEpochDay())
            _report.value = BelastingdienstAggregator.aggregate(q, tx)
        }
    }
}
