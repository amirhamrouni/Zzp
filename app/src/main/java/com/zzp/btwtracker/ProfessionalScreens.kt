package com.zzp.btwtracker

import android.content.Intent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zzp.btwtracker.data.CustomerEntity
import com.zzp.btwtracker.data.InvoiceEntity
import com.zzp.btwtracker.data.ReceiptInboxEntity
import com.zzp.btwtracker.data.TransactionEntity
import com.zzp.btwtracker.tax.BelastingdienstReport
import com.zzp.btwtracker.export.InvoiceExportService
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private val nlCurrency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("nl-NL"))

private fun money(cents: Long): String = nlCurrency.format(cents / 100.0)

@Composable
fun ProfessionalOverviewScreen(
    transactions: List<TransactionEntity>,
    report: BelastingdienstReport,
    invoices: List<InvoiceEntity>,
    pendingReceipts: List<ReceiptInboxEntity>,
    onOpenReceipts: () -> Unit,
    onOpenInvoices: () -> Unit,
    onOpenReport: () -> Unit
) {
    val quarterTransactions = transactions.filter {
        val date = LocalDate.ofEpochDay(it.dateEpochDay)
        !date.isBefore(report.quarter.start) && !date.isAfter(report.quarter.end)
    }
    val income = quarterTransactions.filter { it.type == "INCOME" }.sumOf { it.netCents }
    val expenses = quarterTransactions.filter { it.type == "EXPENSE" }.sumOf { it.netCents }
    val profit = income - expenses
    val overdue = invoices.count { it.status == "OVERDUE" || (it.status == "OPEN" && it.dueDateEpochDay < LocalDate.now().toEpochDay()) }
    val deadline = when (report.quarter.number) {
        1 -> LocalDate.of(report.quarter.year, 4, 30)
        2 -> LocalDate.of(report.quarter.year, 7, 31)
        3 -> LocalDate.of(report.quarter.year, 10, 31)
        else -> LocalDate.of(report.quarter.year + 1, 1, 31)
    }
    val days = ChronoUnit.DAYS.between(LocalDate.now(), deadline)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Overzicht", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${report.quarter} · ${if (days >= 0) "nog $days dagen" else "deadline verstreken"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null)
                        Text("  BTW", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        if (report.payableCents >= 0) "${money(report.payableCents)} te betalen" else "${money(-report.payableCents)} terug te vragen",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Deadline ${deadline.dayOfMonth} ${deadline.month.name.lowercase()} ${deadline.year}")
                    TextButton(onClick = onOpenReport) { Text("Bekijk aangifte →") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Omzet", money(income), Modifier.weight(1f))
                MetricCard("Kosten", money(expenses), Modifier.weight(1f))
                MetricCard("Winst", money(profit), Modifier.weight(1f))
            }
        }
        item { Text("Acties voor jou", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (pendingReceipts.isNotEmpty()) {
            item {
                ActionCard(
                    icon = { Icon(Icons.Default.ReceiptLong, null) },
                    title = "${pendingReceipts.size} bonnetjes controleren",
                    subtitle = "Controleer OCR-gegevens voordat je ze boekt.",
                    onClick = onOpenReceipts
                )
            }
        }
        if (overdue > 0) {
            item {
                ActionCard(
                    icon = { Icon(Icons.Default.Warning, null) },
                    title = "$overdue facturen te laat",
                    subtitle = "Bekijk openstaande facturen en betalingen.",
                    onClick = onOpenInvoices
                )
            }
        }
        if (pendingReceipts.isEmpty() && overdue == 0) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Administratie bijgewerkt", fontWeight = FontWeight.SemiBold)
                            Text("Geen urgente acties voor dit moment.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Text("Recent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(transactions.take(6), key = { it.id }) { tx ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tx.description, fontWeight = FontWeight.Medium)
                        Text("${LocalDate.ofEpochDay(tx.dateEpochDay)} · ${tx.vatRate}% BTW", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(money(tx.grossCents), fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ActionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Text("→", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun AdministrationScreen(transactions: List<TransactionEntity>, onDelete: (Long) -> Unit, onCategory: (Long, String) -> Unit) {
    var filter by remember { mutableStateOf("ALL") }
    val filtered = transactions.filter {
        filter == "ALL" || it.type == filter
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Administratie", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == "ALL", { filter = "ALL" }, label = { Text("Alles") })
            FilterChip(filter == "INCOME", { filter = "INCOME" }, label = { Text("Inkomsten") })
            FilterChip(filter == "EXPENSE", { filter = "EXPENSE" }, label = { Text("Uitgaven") })
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { tx ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tx.description, fontWeight = FontWeight.SemiBold)
                            Text(money(tx.grossCents), fontWeight = FontWeight.Bold)
                        }
                        Text("${LocalDate.ofEpochDay(tx.dateEpochDay)} · ${tx.vatRate}% · Rubriek ${tx.taxBox}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("WERK","REIS","KANTOOR","OVERIG").forEach{cat->FilterChip(selected=tx.category==cat,onClick={onCategory(tx.id,cat)},label={Text(cat.lowercase())})}}
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onDelete(tx.id) }) { Text("Verwijderen") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvoicesScreen(vm: MainViewModel, customers: List<CustomerEntity>, invoices: List<InvoiceEntity>) {
    val context = LocalContext.current
    val company by vm.companyProfile.collectAsState()
    var mode by remember { mutableStateOf("LIST") }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var vatRate by remember { mutableStateOf(21) }
    var message by remember { mutableStateOf<String?>(null) }

    var customerName by remember { mutableStateOf("") }
    var customerEmail by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var customerPostal by remember { mutableStateOf("") }
    var customerCity by remember { mutableStateOf("") }
    var customerKvk by remember { mutableStateOf("") }
    var customerVat by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refreshOverdueInvoices() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Facturen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${invoices.count { it.status == "OPEN" || it.status == "OVERDUE" }} openstaand", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { mode = "NEW_INVOICE" }) { Text("+ Factuur") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == "LIST", { mode = "LIST" }, label = { Text("Facturen") })
                FilterChip(mode == "CUSTOMERS", { mode = "CUSTOMERS" }, label = { Text("Klanten") })
            }
        }

        when (mode) {
            "LIST" -> {
                if (invoices.isEmpty()) item { Text("Nog geen facturen. Maak je eerste factuur aan.") }
                items(invoices, key = { it.id }) { invoice ->
                    InvoiceCard(invoice = invoice, onPaid = { vm.markInvoicePaid(invoice.id) }, onDelete = { vm.deleteInvoice(invoice.id) }, onShare = {
                        runCatching {
                            val profile = requireNotNull(company) { "Vul eerst het bedrijfsprofiel in" }
                            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                            val file = File(dir, "factuur-${invoice.invoiceNumber}.pdf")
                            file.writeBytes(InvoiceExportService.pdf(invoice, profile))
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="application/pdf"; putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Factuur delen"))
                        }.onFailure { message = it.message }
                    })
                }
            }
            "CUSTOMERS" -> {
                item {
                    Text("Nieuwe klant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(customerName, { customerName = it }, label = { Text("Naam / bedrijf") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(customerEmail, { customerEmail = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(customerAddress, { customerAddress = it }, label = { Text("Adres") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(customerPostal, { customerPostal = it }, label = { Text("Postcode") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(customerCity, { customerCity = it }, label = { Text("Plaats") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(customerKvk, { customerKvk = it.filter(Char::isDigit).take(8) }, label = { Text("KvK") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(customerVat, { customerVat = it }, label = { Text("BTW-nummer") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            vm.addCustomer(customerName, customerEmail, customerAddress, customerPostal, customerCity, "NL", customerKvk, customerVat, null) {
                                message = if (it.isSuccess) "Klant opgeslagen" else it.exceptionOrNull()?.message
                                if (it.isSuccess) {
                                    customerName = ""; customerEmail = ""; customerAddress = ""; customerPostal = ""; customerCity = ""; customerKvk = ""; customerVat = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Klant opslaan") }
                }
                items(customers, key = { it.id }) { customer ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Business, null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(customer.name, fontWeight = FontWeight.SemiBold)
                                Text(listOfNotNull(customer.email, customer.city, customer.kvkNumber?.let { "KvK $it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.deleteCustomer(customer.id) }) { Text("Verwijder") }
                        }
                    }
                }
            }
            "NEW_INVOICE" -> {
                item {
                    Text("Nieuwe factuur", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (customers.isEmpty()) {
                        Text("Maak eerst een klant aan.")
                        OutlinedButton(onClick = { mode = "CUSTOMERS" }) { Text("Klant toevoegen") }
                    } else {
                        Text("Klant", fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            customers.take(8).forEach { customer ->
                                FilterChip(
                                    selected = selectedCustomerId == customer.id,
                                    onClick = { selectedCustomerId = customer.id },
                                    label = { Text(customer.name) }
                                )
                            }
                        }
                        OutlinedTextField(description, { description = it }, label = { Text("Omschrijving") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(amount, { amount = it }, label = { Text("Bedrag incl. BTW") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(21, 9, 0).forEach { rate ->
                                FilterChip(vatRate == rate, { vatRate = rate }, label = { Text("$rate%") })
                            }
                        }
                        Button(
                            onClick = {
                                val customer = customers.firstOrNull { it.id == selectedCustomerId }
                                val parsed = amount.replace(',', '.').toBigDecimalOrNull()
                                if (customer == null || parsed == null) {
                                    message = "Selecteer een klant en controleer het bedrag."
                                } else {
                                    vm.createInvoice(customer, description, parsed, vatRate, LocalDate.now()) {
                                        message = if (it.isSuccess) "Factuur aangemaakt" else it.exceptionOrNull()?.message
                                        if (it.isSuccess) {
                                            description = ""; amount = ""; selectedCustomerId = null; mode = "LIST"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Factuur aanmaken") }
                        Text("Factuurnummers worden automatisch per jaar opgebouwd (bijv. 2026-001).", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun InvoiceCard(invoice: InvoiceEntity, onPaid: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val effectiveStatus = if (invoice.status == "OPEN" && invoice.dueDateEpochDay < LocalDate.now().toEpochDay()) "OVERDUE" else invoice.status
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold)
                Text(money(invoice.grossCents), fontWeight = FontWeight.Bold)
            }
            Text(invoice.customerName)
            Text(invoice.description, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (effectiveStatus) {
                        "PAID" -> "Betaald"
                        "OVERDUE" -> "Te laat"
                        "DRAFT" -> "Concept"
                        else -> "Open"
                    },
                    color = if (effectiveStatus == "OVERDUE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Row { TextButton(onClick = onShare) { Text("PDF") }; if (effectiveStatus != "PAID") TextButton(onClick = onPaid) { Text("Betaald") }; TextButton(onClick=onDelete){Text("Wis")} }
            }
        }
    }
}

@Composable
fun MoreScreen(onScan: () -> Unit, onReport: () -> Unit, onHours: () -> Unit, onTrips: () -> Unit, onCoach: () -> Unit, onCompany: () -> Unit, onDocuments: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Meer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item { MenuCard("Bonnetjes & OCR", "Scan en controleer zakelijke bonnetjes.", onScan) }
        item { MenuCard("BTW-aangifte", "Bekijk rubrieken, controleer en exporteer PDF/CSV.", onReport) }
        item { MenuCard("ZZP Coach", "Persoonlijke acties voor btw, KOR, uren en cashflow.", onCoach) }
        item { MenuCard("Urenregistratie", "Registreer zakelijke uren en volg het urencriterium.", onHours) }
        item { MenuCard("Rittenregistratie", "Bewaar zakelijke kilometers en bereken €0,25/km.", onTrips) }
        item { MenuCard("Bedrijfsprofiel", "KvK, BTW-id, IBAN en factuurgegevens.", onCompany) }
        item { MenuCard("Documenten & archief", "Bonnen, facturen en bankbestanden per kwartaal.", onDocuments) }
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("→", style = MaterialTheme.typography.titleLarge)
        }
    }
}
