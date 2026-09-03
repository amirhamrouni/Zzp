package com.zzp.btwtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzp.btwtracker.ui.ZzpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZzpTheme { ZzpProfessionalApp() } }
    }
}

private enum class AppScreen {
    OVERVIEW,
    ADMINISTRATION,
    BOOKING,
    SCANNER,
    INVOICES,
    REPORT,
    HOURS,
    TRIPS,
    COACH,
    COMPANY,
    DOCUMENTS,
    MORE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZzpProfessionalApp(vm: MainViewModel = viewModel()) {
    var screen by remember { mutableStateOf(AppScreen.OVERVIEW) }
    var quickAddOpen by remember { mutableStateOf(false) }

    val transactions by vm.transactions.collectAsState()
    val report by vm.report.collectAsState()
    val customers by vm.customers.collectAsState()
    val invoices by vm.invoices.collectAsState()
    val pendingReceipts by vm.pendingReceipts.collectAsState()
    val workSessions by vm.workSessions.collectAsState()
    val businessTrips by vm.businessTrips.collectAsState()
    val companyProfile by vm.companyProfile.collectAsState()
    val documents by vm.documents.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == AppScreen.OVERVIEW,
                    onClick = { screen = AppScreen.OVERVIEW },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Overzicht") }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.ADMINISTRATION,
                    onClick = { screen = AppScreen.ADMINISTRATION },
                    icon = { Icon(Icons.Default.ReceiptLong, null) },
                    label = { Text("Administratie") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { quickAddOpen = true },
                    icon = {
                        FloatingActionButton(onClick = { quickAddOpen = true }) {
                            Icon(Icons.Default.Add, "Nieuwe boeking")
                        }
                    },
                    label = null
                )
                NavigationBarItem(
                    selected = screen == AppScreen.INVOICES,
                    onClick = { screen = AppScreen.INVOICES },
                    icon = { Icon(Icons.Default.Description, null) },
                    label = { Text("Facturen") }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.MORE || screen == AppScreen.REPORT || screen == AppScreen.SCANNER,
                    onClick = { screen = AppScreen.MORE },
                    icon = { Icon(Icons.Default.MoreHoriz, null) },
                    label = { Text("Meer") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                AppScreen.OVERVIEW -> ProfessionalOverviewScreen(
                    transactions = transactions,
                    report = report,
                    invoices = invoices,
                    pendingReceipts = pendingReceipts,
                    onOpenReceipts = { screen = AppScreen.SCANNER },
                    onOpenInvoices = { screen = AppScreen.INVOICES },
                    onOpenReport = { screen = AppScreen.REPORT }
                )
                AppScreen.ADMINISTRATION -> AdministrationScreen(transactions, vm::delete, vm::setTransactionCategory)
                AppScreen.BOOKING -> BookingScreen { draft ->
                    vm.save(draft) { if (it.isSuccess) screen = AppScreen.OVERVIEW }
                }
                AppScreen.SCANNER -> ReceiptScannerScreen(vm) { screen = AppScreen.OVERVIEW }
                AppScreen.INVOICES -> InvoicesScreen(vm, customers, invoices)
                AppScreen.REPORT -> VatReportScreen(vm, report)
                AppScreen.HOURS -> HoursScreen(vm, workSessions)
                AppScreen.TRIPS -> TripsScreen(vm, businessTrips)
                AppScreen.COACH -> CoachScreen(transactions, invoices, pendingReceipts, workSessions, report.payableCents)
                AppScreen.COMPANY -> CompanyProfileScreen(vm, companyProfile)
                AppScreen.DOCUMENTS -> DocumentsScreen(vm, documents)
                AppScreen.MORE -> MoreScreen(
                    onScan = { screen = AppScreen.SCANNER },
                    onReport = { screen = AppScreen.REPORT },
                    onHours = { screen = AppScreen.HOURS },
                    onTrips = { screen = AppScreen.TRIPS },
                    onCoach = { screen = AppScreen.COACH },
                    onCompany = { screen = AppScreen.COMPANY },
                    onDocuments = { screen = AppScreen.DOCUMENTS }
                )
            }
        }
    }

    if (quickAddOpen) {
        ModalBottomSheet(onDismissRequest = { quickAddOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nieuwe boeking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Wat wil je toevoegen?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ZzpPrimaryButton(text = "Bon scannen met OCR", onClick = {
                    quickAddOpen = false
                    screen = AppScreen.SCANNER
                }, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedButton(onClick = {
                    quickAddOpen = false
                    screen = AppScreen.BOOKING
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.EditNote, null)
                    Text("  Handmatig boeken")
                }
                Text("OCR herkent bedrag, BTW, datum en KvK-nummer. Je controleert alles vóór het boeken.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
