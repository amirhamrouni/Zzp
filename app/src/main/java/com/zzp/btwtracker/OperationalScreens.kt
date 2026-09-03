package com.zzp.btwtracker

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zzp.btwtracker.export.QuarterExportService
import com.zzp.btwtracker.ocr.ReceiptOcrScanner
import com.zzp.btwtracker.ocr.ReceiptScanResult
import com.zzp.btwtracker.tax.BelastingdienstReport
import com.zzp.btwtracker.tax.TransactionDraft
import com.zzp.btwtracker.tax.TransactionType
import com.zzp.btwtracker.tax.VatTreatment
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun BookingScreen(onSave: (TransactionDraft) -> Unit) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(21) }
    var treatment by remember { mutableStateOf(VatTreatment.DOMESTIC) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var kvk by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Nieuwe boeking", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == TransactionType.INCOME, { type = TransactionType.INCOME; treatment = VatTreatment.DOMESTIC }, label = { Text("Omzet") })
                FilterChip(type == TransactionType.EXPENSE, { type = TransactionType.EXPENSE; treatment = VatTreatment.DOMESTIC }, label = { Text("Kosten") })
            }
        }
        item { OutlinedTextField(description, { description = it }, label = { Text("Omschrijving") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(amount, { amount = it }, label = { Text("Bedrag incl. BTW") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { RateSelectorPublic(rate) { rate = it } }
        item {
            Text("BTW-behandeling", fontWeight = FontWeight.SemiBold)
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
            Column { options.forEach { (value, label) -> FilterChip(treatment == value, { treatment = value }, label = { Text(label) }) } }
        }
        item { OutlinedTextField(date, { date = it }, label = { Text("Datum (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(kvk, { kvk = it.filter(Char::isDigit).take(8) }, label = { Text("KvK (optioneel)") }, modifier = Modifier.fillMaxWidth()) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(onClick = {
                val parsedAmount = amount.replace(',', '.').toBigDecimalOrNull()
                val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                if (parsedAmount == null || parsedDate == null) error = "Controleer bedrag en datum."
                else onSave(TransactionDraft(type, description, parsedAmount, rate, treatment, parsedDate, kvkNumber = kvk.ifBlank { null }))
            }, modifier = Modifier.fillMaxWidth()) { Text("Boeking opslaan") }
        }
    }
}

@Composable
private fun RateSelectorPublic(selected: Int, onSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(21, 9, 0).forEach { rate ->
            FilterChip(selected == rate, { onSelected(rate) }, label = { Text("$rate%") })
        }
    }
}

@Composable
fun ReceiptScannerScreen(
    vm: MainViewModel,
    onBooked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { ReceiptOcrScanner() }
    var scanning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ReceiptScanResult?>(null) }
    var rate by remember { mutableStateOf(21) }
    var error by remember { mutableStateOf<String?>(null) }
    val inbox by vm.pendingReceipts.collectAsState()

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
        if (granted) camera.launch(null) else error = "Cameratoestemming is nodig."
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scanning=true; scope.launch { runCatching { scanner.scan(context,it) }.onSuccess { result=it }.onFailure { error=it.message }; scanning=false } }
    }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Bon scannen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera.launch(null)
                else permission.launch(Manifest.permission.CAMERA)
            }, modifier = Modifier.weight(1f), enabled = !scanning) {
                Icon(Icons.Default.CameraAlt, null)
                Text("  Camera")
            };Button(onClick={gallery.launch("image/*")},modifier=Modifier.weight(1f),enabled=!scanning){Text("Galerij")}}
        }
        if (scanning) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text("  Bon herkennen…") } }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        result?.let { scan ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("OCR-resultaat", fontWeight = FontWeight.Bold)
                        Text("Leverancier: ${scan.merchantName ?: "niet gevonden"} · zekerheid ${scan.confidence}%")
                        Text("Totaal: ${scan.totalAmount ?: "niet gevonden"}")
                        Text("BTW: ${scan.vatAmount ?: "niet gevonden"}")
                        Text("KvK: ${scan.kvkNumber ?: "niet gevonden"}")
                        Text("Datum: ${scan.date ?: "niet gevonden"}")
                    }
                }
            }
            item { RateSelectorPublic(rate) { rate = it } }
            item {
                Button(onClick = {
                    val total = scan.totalAmount
                    if (total == null) error = "Totaalbedrag niet gevonden."
                    else vm.save(
                        TransactionDraft(
                            type = TransactionType.EXPENSE,
                            description = scan.merchantName ?: "Gescande bon",
                            grossAmount = total,
                            vatRate = rate,
                            treatment = VatTreatment.DOMESTIC,
                            date = scan.date ?: LocalDate.now(),
                            kvkNumber = scan.kvkNumber,
                            explicitVatAmount = scan.vatAmount
                        )
                    ) { if (it.isSuccess) onBooked() }
                }, modifier = Modifier.fillMaxWidth()) { Text("Boek als kosten") }
            }
            item {
                TextButton(onClick = {
                    val totalCents = scan.totalAmount?.movePointRight(2)?.toLong()
                    val vatCents = scan.vatAmount?.movePointRight(2)?.toLong()
                    vm.addReceiptToInbox(
                        com.zzp.btwtracker.data.ReceiptInboxEntity(
                            totalCents = totalCents,
                            merchantName = scan.merchantName,
                            vatCents = vatCents,
                            kvkNumber = scan.kvkNumber,
                            dateEpochDay = scan.date?.toEpochDay(),
                            rawText = scan.rawText
                        )
                    ) { if (it.isSuccess) onBooked() }
                }) { Text("Bewaar in OCR Inbox voor later") }
            }
        }
        if(inbox.isNotEmpty()) item { Text("Te controleren (${inbox.size})",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
        items(inbox,key={it.id}) { item ->
            var description by remember(item.id){mutableStateOf(item.merchantName ?: "Gescande bon")}; var itemRate by remember(item.id){mutableStateOf(21)}; var itemError by remember(item.id){mutableStateOf<String?>(null)}
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                OutlinedTextField(description,{description=it},label={Text("Omschrijving")},modifier=Modifier.fillMaxWidth())
                Text("Totaal: ${item.totalCents?.let { "€ %.2f".format(it/100.0) } ?: "ontbreekt"} · BTW: ${item.vatCents?.let { "€ %.2f".format(it/100.0) } ?: "onbekend"}")
                RateSelectorPublic(itemRate){itemRate=it}; itemError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={vm.dismissReceipt(item.id)}){Text("Verwijder")};Button(onClick={vm.bookReceipt(item,description,itemRate){r->itemError=r.exceptionOrNull()?.message}}){Text("Boeken")}}
            }}
        }
    }
}

@Composable
fun VatReportScreen(vm: MainViewModel, report: BelastingdienstReport) {
    val context = LocalContext.current
    var csvBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(csvBytes ?: byteArrayOf()) }; status = "CSV opgeslagen" }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(pdfBytes ?: byteArrayOf()) }; status = "PDF opgeslagen" }
    }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("BTW-aangifte", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.moveQuarter(-1) }) { Text("← Vorige") }
                Text(report.quarter.toString(), fontWeight = FontWeight.Bold)
                TextButton(onClick = { vm.moveQuarter(1) }) { Text("Volgende →") }
            }
        }
        item { VatRow("1a Hoog tarief", report.box1aTurnoverCents, report.box1aVatCents) }
        item { VatRow("1b Laag tarief", report.box1bTurnoverCents, report.box1bVatCents) }
        item { VatRow("1e 0% / niet belast", report.box1eTurnoverCents, null) }
        item { VatRow("3b EU omzet / verlegd", report.box3bTurnoverCents, null) }
        item { VatRow("4a Buiten EU", report.box4aBaseCents, report.box4aVatCents) }
        item { VatRow("4b Binnen EU", report.box4bBaseCents, report.box4bVatCents) }
        item { VatRow("5a Verschuldigde BTW", 0, report.vatDue5aCents) }
        item { VatRow("5b Voorbelasting", 0, report.inputVat5bCents) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (report.payableCents >= 0) "Te betalen" else "Terug te vragen")
                    Text("€ %.2f".format(kotlin.math.abs(report.payableCents) / 100.0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Button(onClick = {
                csvBytes = QuarterExportService.csv(report)
                csvLauncher.launch("BTW-${report.quarter}.csv")
            }, modifier = Modifier.fillMaxWidth()) { Text("Exporteer CSV") }
        }
        item {
            Button(onClick = {
                pdfBytes = QuarterExportService.pdf(report)
                pdfLauncher.launch("BTW-${report.quarter}.pdf")
            }, modifier = Modifier.fillMaxWidth()) { Text("Exporteer PDF") }
        }
        status?.let { item { Text(it) } }
    }
}

@Composable
private fun VatRow(label: String, baseCents: Long, vatCents: Long?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f))
            if (baseCents != 0L) Text("€ %.2f".format(baseCents / 100.0))
            vatCents?.let { Text("  € %.2f".format(it / 100.0), fontWeight = FontWeight.SemiBold) }
        }
    }
}
