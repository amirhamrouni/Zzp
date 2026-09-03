package com.zzp.btwtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zzp.btwtracker.data.CompanyProfileEntity

@Composable fun CompanyProfileScreen(vm: MainViewModel, saved: CompanyProfileEntity?) {
    var profile by remember(saved) { mutableStateOf(saved ?: CompanyProfileEntity()) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Bedrijfsprofiel", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold); Text("Deze gegevens komen op je facturen.", color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Field("Bedrijfsnaam", profile.tradeName){profile=profile.copy(tradeName=it)} }
        item { Field("Naam ondernemer", profile.ownerName){profile=profile.copy(ownerName=it)} }
        item { Field("Adres", profile.address){profile=profile.copy(address=it)} }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ Box(Modifier.weight(1f)){Field("Postcode",profile.postalCode){profile=profile.copy(postalCode=it)}}; Box(Modifier.weight(1f)){Field("Plaats",profile.city){profile=profile.copy(city=it)}} } }
        item { Field("KvK-nummer",profile.kvkNumber,{profile=profile.copy(kvkNumber=it.filter(Char::isDigit).take(8))}) }
        item { Field("BTW-id",profile.vatId){profile=profile.copy(vatId=it)} }
        item { Field("IBAN",profile.iban){profile=profile.copy(iban=it)} }
        item { Field("E-mail",profile.email){profile=profile.copy(email=it)} }
        item { Field("Betaaltermijn (dagen)",profile.paymentTermDays.toString()){profile=profile.copy(paymentTermDays=it.filter(Char::isDigit).toIntOrNull()?:14)} }
        item { Button(onClick={vm.saveCompanyProfile(profile){message=if(it.isSuccess)"Bedrijfsprofiel opgeslagen" else it.exceptionOrNull()?.message}},modifier=Modifier.fillMaxWidth()){Text("Opslaan")} }
        message?.let { item { Text(it,color=MaterialTheme.colorScheme.primary) } }
    }
}

@Composable private fun Field(label:String,value:String,onChange:(String)->Unit){OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true)}
