package com.zzp.btwtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zzp.btwtracker.coach.ZzpCoach
import com.zzp.btwtracker.data.*
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun HoursScreen(vm: MainViewModel, sessions: List<WorkSessionEntity>) {
    var project by remember { mutableStateOf("") }; var minutes by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    val yearMinutes = sessions.filter { LocalDate.ofEpochDay(it.dateEpochDay).year == LocalDate.now().year }.sumOf { it.minutes }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ZzpScreenHeader("Urenregistratie", "Volg zakelijke uren en je urencriterium") }
        item { ProgressCard("Urencriterium ${LocalDate.now().year}", yearMinutes / 60f, 1225f, "${yearMinutes / 60} van 1.225 uur") }
        item { OutlinedTextField(project, { project = it }, label = { Text("Project") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minuten") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(note, { note = it }, label = { Text("Werkzaamheden") }, modifier = Modifier.fillMaxWidth()) }
        item { ZzpPrimaryButton("Uren opslaan", { vm.addWorkSession(project, minutes.toIntOrNull() ?: 0, note, LocalDate.now()) { if (it.isSuccess) { project=""; minutes=""; note="" } } }, Modifier.fillMaxWidth(), project.isNotBlank() && (minutes.toIntOrNull() ?: 0) > 0) }
        items(sessions, key = { it.id }) { s -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(s.project, fontWeight = FontWeight.SemiBold); Text(LocalDate.ofEpochDay(s.dateEpochDay).toString(), style = MaterialTheme.typography.bodySmall) }; Text("${s.minutes / 60}u ${s.minutes % 60}m") } } }
    }
}

@Composable
fun TripsScreen(vm: MainViewModel, trips: List<BusinessTripEntity>) {
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }; var purpose by remember { mutableStateOf("") }; var km by remember { mutableStateOf("") }
    val yearTrips = trips.filter { LocalDate.ofEpochDay(it.dateEpochDay).year == LocalDate.now().year }
    val tenths = yearTrips.sumOf { it.kilometersTimes10 }; val deductionCents = tenths * 25 / 10
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ZzpScreenHeader("Rittenregistratie", "Bewaar elke zakelijke rit en aftrekindicatie") }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Zakelijke kilometers ${LocalDate.now().year}"); Text("${tenths / 10.0} km", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Aftrekindicatie € %.2f bij €0,25/km".format(deductionCents / 100.0)) } } }
        item { OutlinedTextField(from, { from = it }, label={Text("Van")}, modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(to, { to = it }, label={Text("Naar")}, modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(purpose, { purpose = it }, label={Text("Zakelijk doel")}, modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(km, { km = it.filter { c -> c.isDigit() || c==',' || c=='.' } }, label={Text("Kilometers")}, modifier=Modifier.fillMaxWidth()) }
        item { ZzpPrimaryButton("Rit opslaan", { vm.addBusinessTrip(from,to,purpose,km.replace(',','.').toBigDecimalOrNull() ?: BigDecimal.ZERO,LocalDate.now()) { if(it.isSuccess){from="";to="";purpose="";km=""} } }, Modifier.fillMaxWidth(), from.isNotBlank() && to.isNotBlank() && (km.replace(',','.').toDoubleOrNull() ?: 0.0) > 0.0) }
        items(trips, key={it.id}) { t -> Card(Modifier.fillMaxWidth()){ Column(Modifier.padding(14.dp)){ Text("${t.origin} → ${t.destination}", fontWeight=FontWeight.SemiBold); Text("${t.purpose} · ${t.kilometersTimes10/10.0} km", style=MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable
fun CoachScreen(transactions: List<TransactionEntity>, invoices: List<InvoiceEntity>, receipts: List<ReceiptInboxEntity>, sessions: List<WorkSessionEntity>, vatReserveCents: Long) {
    val year = LocalDate.now().year
    val annualIncome = transactions.filter { it.type=="INCOME" && LocalDate.ofEpochDay(it.dateEpochDay).year==year }.sumOf { it.netCents }
    val annualMinutes = sessions.filter { LocalDate.ofEpochDay(it.dateEpochDay).year==year }.sumOf { it.minutes }
    val insights = ZzpCoach.build(annualIncome, annualMinutes, vatReserveCents.coerceAtLeast(0), invoices, receipts)
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { ZzpScreenHeader("ZZP Coach", "Concrete acties uit je eigen administratie") }
        item { ProgressCard("KOR-monitor", annualIncome/100f, ZzpCoach.KOR_LIMIT_CENTS/100f, "€ %.2f van €20.000".format(annualIncome/100.0)) }
        items(insights) { insight -> Card(Modifier.fillMaxWidth()){ Column(Modifier.padding(16.dp)){ Text(insight.title, fontWeight=FontWeight.Bold); Text(insight.detail); Text(insight.level, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary) } } }
        item { Text("Indicaties zijn administratieve hulpmiddelen en geen belastingadvies.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun ProgressCard(title:String, value:Float, target:Float, label:String) {
    Card(Modifier.fillMaxWidth()){ Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){ Text(title,fontWeight=FontWeight.SemiBold); LinearProgressIndicator(progress={ (value/target).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth()); Text(label,style=MaterialTheme.typography.bodySmall) } }
}
