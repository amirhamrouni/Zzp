package com.zzp.btwtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzp.btwtracker.data.TransactionEntity
import com.zzp.btwtracker.export.QuarterExportService
import com.zzp.btwtracker.ocr.ReceiptOcrScanner
import com.zzp.btwtracker.ocr.ReceiptScanResult
import com.zzp.btwtracker.tax.BelastingdienstReport
import com.zzp.btwtracker.tax.TransactionDraft
import com.zzp.btwtracker.tax.TransactionType
import com.zzp.btwtracker.tax.VatTreatment
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ZzpBtwTrackerApp() } }
    }
}

private enum class AppScreen { OVERVIEW, ADD, SCAN, REPORT }

@Composable
private fun ZzpBtwTrackerApp(vm: MainViewModel = viewModel()) {
    var screen by remember { mutableStateOf(AppScreen.OVERVIEW) }
    val transactions by vm.transactions.collectAsState()
    val report by vm.report.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = screen == AppScreen.OVERVIEW, onClick = { screen = AppScreen.OVERVIEW }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Overzicht") })
                NavigationBarItem(selected = screen == AppScreen.ADD, onClick = { screen = AppScreen.ADD }, icon = { Icon(Icons.Default.AddCircle, null) }, label = { Text("Boeken") })
                NavigationBarItem(selected = screen == AppScreen.SCAN, onClick = { screen = AppScreen.SCAN }, icon = { Icon(Icons.Default.CameraAlt, null) }, label = { Text("Scannen") })
                NavigationBarItem(selected = screen == AppScreen.REPORT, onClick = { screen = AppScreen.REPORT }, icon = { Icon(Icons.Default.Assessment, null) }, label = { Text("Aangifte") })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                AppScreen.OVERVIEW -> OverviewScreen(transactions, report, onDelete = vm::delete)
                AppScreen.ADD -> TransactionForm(onSave = { vm.save(it) { result -> if (result.isSuccess) screen = AppScreen.OVERVIEW } })
                AppScreen.SCAN -> ScannerScreen(onSave = { vm.save(it) { result -> if (result.isSuccess) screen = AppScreen.OVERVIEW } })
                AppScreen.REPORT -> ReportScreen(vm, report)
            }
        }
    }
}

@Composable
private fun Page(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun OverviewScreen(transactions: List<TransactionEntity>, report: BelastingdienstReport, onDelete: (Long) -> Unit) = Page("ZZP BTW Tracker") {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("${report.quarter}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Omzet hoog", euro(report.box1aTurnoverCents), Modifier.weight(1f))
                SummaryCard("BTW saldo", euro(report.payableCents), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text("Recente transacties", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        if (transactions.isEmpty()) item { Text("Nog geen transacties. Voeg een boeking toe of scan een bon.") }
        items(transactions.take(20), key = { it.id }) { tx ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, null)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(tx.description, fontWeight = FontWeight.Medium)
                        Text("${LocalDate.ofEpochDay(tx.dateEpochDay)} · ${tx.vatRate}% · Rubriek ${tx.taxBox}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(euro(tx.grossCents), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onDelete(tx.id) }) { Icon(Icons.Default.Delete, "Verwijderen") }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TransactionForm(onSave: (TransactionDraft) -> Unit) = Page("Nieuwe boeking") {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(21) }
    var treatment by remember { mutableStateOf(VatTreatment.DOMESTIC) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var kvk by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME; treatment = VatTreatment.DOMESTIC }, label = { Text("Omzet") })
                FilterChip(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE; treatment = VatTreatment.DOMESTIC }, label = { Text("Kosten") })
            }
        }
        item { OutlinedTextField(description, { description = it }, label = { Text("Omschrijving") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(amount, { amount = it }, label = { Text("Bedrag incl. btw / factuurbedrag") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { RateSelector(rate) { rate = it } }
        item {
            Text("BTW-behandeling", fontWeight = FontWeight.Medium)
            TreatmentSelector(type, treatment) { treatment = it }
        }
        item { OutlinedTextField(date, { date = it }, label = { Text("Datum (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(kvk, { kvk = it.filter(Char::isDigit).take(8) }, label = { Text("KvK (optioneel)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(onClick = {
                val parsedAmount = amount.replace(',', '.').toBigDecimalOrNull()
                val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                if (parsedAmount == null || parsedDate == null) error = "Controleer bedrag en datum."
                else {
                    error = null
                    onSave(TransactionDraft(type, description, parsedAmount, rate, treatment, parsedDate, kvkNumber = kvk.ifBlank { null }))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Boeking opslaan") }
        }
    }
}

@Composable
private fun RateSelector(selected: Int, onSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(21 to "21% Hoog", 9 to "9% Laag", 0 to "0% Nul").forEach { (rate, label) ->
            FilterChip(selected = selected == rate, onClick = { onSelected(rate) }, label = { Text(label) })
        }
    }
}

@Composable
private fun TreatmentSelector(type: TransactionType, selected: VatTreatment, onSelected: (VatTreatment) -> Unit) {
    val options = if (type == TransactionType.INCOME) listOf(
        VatTreatment.DOMESTIC to "Binnenland",
        VatTreatment.EU_REVERSE_CHARGE to "EU · btw verlegd",
        VatTreatment.EXEMPT to "Vrijgesteld"
    ) else listOf(
        VatTreatment.DOMESTIC to "Binnenland",
        VatTreatment.EU_PURCHASE to "Inkoop EU",
        VatTreatment.NON_EU_PURCHASE to "Inkoop buiten EU",
        VatTreatment.EXEMPT to "Vrijgesteld"
    )
    Column {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ScannerScreen(onSave: (TransactionDraft) -> Unit) = Page("Bon scannen") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { ReceiptOcrScanner() }
    var scanning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ReceiptScanResult?>(null) }
    var rate by remember { mutableStateOf(21) }
    var error by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scanning = true
            scope.launch {
                runCatching { scanner.scan(bitmap) }
                    .onSuccess { result = it }
                    .onFailure { error = it.message ?: "OCR mislukt" }
                scanning = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(null) else error = "Cameratoestemming is nodig om een bon te scannen."
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera.launch(null)
                else permission.launch(Manifest.permission.CAMERA)
            }, modifier = Modifier.fillMaxWidth(), enabled = !scanning) {
                Icon(Icons.Default.CameraAlt, null)
                Text("  Maak foto van bon")
            }
        }
        if (scanning) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text("  Tekst en BTW herkennen…") } }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        result?.let { scan ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Gevonden gegevens", fontWeight = FontWeight.Bold)
                        Text("Totaal: ${scan.totalAmount?.let { "€ $it" } ?: "niet gevonden"}")
                        Text("BTW: ${scan.vatAmount?.let { "€ $it" } ?: "niet gevonden"}")
                        Text("KvK: ${scan.kvkNumber ?: "niet gevonden"}")
                        Text("Datum: ${scan.date ?: "niet gevonden"}")
                    }
                }
            }
            item { RateSelector(rate) { rate = it } }
            item {
                Button(onClick = {
                    val total = scan.totalAmount
                    if (total == null) error = "Totaalbedrag niet gevonden. Voeg de boeking handmatig toe."
                    else onSave(TransactionDraft(
                        type = TransactionType.EXPENSE,
                        description = "Gescande bon",
                        grossAmount = total,
                        vatRate = rate,
                        treatment = VatTreatment.DOMESTIC,
                        date = scan.date ?: LocalDate.now(),
                        kvkNumber = scan.kvkNumber,
                        explicitVatAmount = scan.vatAmount
                    ))
                }, modifier = Modifier.fillMaxWidth()) { Text("Controleer en sla als kosten op") }
            }
            item { Text("OCR is een hulpmiddel. Controleer bedragen altijd met de originele bon.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ReportScreen(vm: MainViewModel, report: BelastingdienstReport) = Page("BTW-aangifte") {
    val context = LocalContext.current
    var csvBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { runCatching { context.contentResolver.openOutputStream(it)?.use { out -> out.write(csvBytes ?: byteArrayOf()) } }.onSuccess { status = "CSV opgeslagen" }.onFailure { status = "Opslaan mislukt: ${it.message}" } }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { runCatching { context.contentResolver.openOutputStream(it)?.use { out -> out.write(pdfBytes ?: byteArrayOf()) } }.onSuccess { status = "PDF opgeslagen" }.onFailure { status = "Opslaan mislukt: ${it.message}" } }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.moveQuarter(-1) }) { Text("← Vorige") }
                Text(report.quarter.toString(), fontWeight = FontWeight.Bold)
                TextButton(onClick = { vm.moveQuarter(1) }) { Text("Volgende →") }
            }
        }
        item { ReportRow("1a Hoog tarief", report.box1aTurnoverCents, report.box1aVatCents) }
        item { ReportRow("1b Laag tarief", report.box1bTurnoverCents, report.box1bVatCents) }
        item { ReportRow("1e 0% / niet belast", report.box1eTurnoverCents, null) }
        item { ReportRow("3b EU omzet / verlegd", report.box3bTurnoverCents, null) }
        item { ReportRow("4a Inkoop buiten EU", report.box4aBaseCents, report.box4aVatCents) }
        item { ReportRow("4b Inkoop binnen EU", report.box4bBaseCents, report.box4bVatCents) }
        item { ReportRow("5a Verschuldigde BTW", null, report.vatDue5aCents) }
        item { ReportRow("5b Voorbelasting", null, report.inputVat5bCents) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (report.payableCents >= 0) "Te betalen" else "Terug te vragen")
                    Text(euro(kotlin.math.abs(report.payableCents)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { csvBytes = QuarterExportService.csv(report); csvLauncher.launch("BTW-${report.quarter}.csv") }, modifier = Modifier.weight(1f)) { Text("CSV") }
                Button(onClick = { pdfBytes = QuarterExportService.pdf(report); pdfLauncher.launch("BTW-${report.quarter}.pdf") }, modifier = Modifier.weight(1f)) { Text("PDF") }
            }
        }
        status?.let { item { Text(it) } }
        item { Text("Gebaseerd op de ingevoerde administratie. Dit is geen automatische indiening bij de Belastingdienst.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ReportRow(label: String, baseCents: Long?, vatCents: Long?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                baseCents?.let { Text("Omzet: ${euro(it)}") }
                vatCents?.let { Text("BTW: ${euro(it)}", fontWeight = FontWeight.Medium) }
            }
        }
    }
}

private fun euro(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("nl", "NL")).format(cents / 100.0)
