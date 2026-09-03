package com.zzp.btwtracker

import android.content.Intent
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zzp.btwtracker.data.DocumentEntity
import java.time.LocalDate

@Composable fun DocumentsScreen(vm: MainViewModel, documents: List<DocumentEntity>) {
    val context=LocalContext.current; var category by remember{mutableStateOf("BON")}; var status by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{
        runCatching { context.contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name=context.contentResolver.query(it,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c:Cursor->if(c.moveToFirst())c.getString(0) else null}?:"document"
        val now=LocalDate.now(); vm.archiveDocument(DocumentEntity(uri=it.toString(),displayName=name,mimeType=context.contentResolver.getType(it)?:"application/octet-stream",category=category,year=now.year,quarter=(now.monthValue-1)/3+1)){r->status=if(r.isSuccess)"Document opgeslagen" else r.exceptionOrNull()?.message}
    }}
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{ZzpScreenHeader("Documenten", "Bewaar originele bestanden per jaar en kwartaal")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("BON","FACTUUR","BANK","OVERIG").forEach{FilterChip(category==it,{category=it},label={Text(it.lowercase().replaceFirstChar { c -> c.uppercase() })})}}}
        item{ZzpPrimaryButton("Document toevoegen", {picker.launch(arrayOf("application/pdf","image/*","text/csv"))}, Modifier.fillMaxWidth())}
        status?.let{item{Text(it,color=MaterialTheme.colorScheme.primary)}}
        if(documents.isEmpty())item{ZzpEmptyState("Je archief is nog leeg", "Voeg bonnetjes, facturen, bankbestanden of CSV-exports toe.")}
        items(documents,key={it.id}){d->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(d.displayName,fontWeight=FontWeight.SemiBold);Text("${d.year} · Q${d.quarter}")};Text(d.category,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(d.uri)).apply{setDataAndType(android.net.Uri.parse(d.uri),d.mimeType);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})}}){Text("Open")};TextButton(onClick={vm.deleteDocument(d.id)}){Text("Verwijder")}}}}}
    }
}
