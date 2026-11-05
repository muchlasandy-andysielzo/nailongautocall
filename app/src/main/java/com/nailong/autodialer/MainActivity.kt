package com.nailong.autodialer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContactCall(
    val number: String,
    var attempts: Int = 0,
    var status: String = "Menunggu",
    var lastUpdate: String = "-"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NailongApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NailongApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts = remember { mutableStateListOf<ContactCall>() }
    val logs = remember { mutableStateListOf<String>() }
    var interval by remember { mutableStateOf("10") }
    var maxRedial by remember { mutableStateOf("2") }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("Import file Excel (.xlsx) atau CSV untuk memulai.") }
    var showResult by remember { mutableStateOf(false) }
    var pendingIndex by remember { mutableIntStateOf(-1) }

    fun timestamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    fun record(index: Int, result: String) {
        if (index !in contacts.indices) return
        val c = contacts[index]
        c.attempts += 1
        c.status = result
        c.lastUpdate = timestamp()
        logs.add("${c.number},${c.attempts},$result,${c.lastUpdate}")
        val max = maxRedial.toIntOrNull()?.coerceIn(1, 10) ?: 2
        if ((result == "Tidak dijawab" || result == "Sibuk" || result == "Gagal") && c.attempts < max) {
            c.status = "Redial menunggu"
        }
        pendingIndex = -1
        showResult = false
    }

    fun nextCall() {
        val idx = contacts.indexOfFirst { it.status == "Menunggu" || it.status == "Redial menunggu" }
        if (idx < 0) {
            running = false
            paused = false
            currentIndex = -1
            message = "Semua nomor selesai diproses."
            return
        }
        currentIndex = idx
        pendingIndex = idx
        val uri = Uri.fromParts("tel", contacts[idx].number, null)
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, uri))
            message = "Dialer dibuka untuk ${contacts[idx].number}. Lakukan panggilan, lalu kembali dan pilih hasilnya."
        } catch (_: Exception) {
            message = "Tidak dapat membuka aplikasi telepon."
            contacts[idx].status = "Gagal membuka dialer"
            pendingIndex = -1
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val name = uri.lastPathSegment?.lowercase() ?: ""
                val imported = if (name.endsWith(".csv") || name.contains("csv")) {
                    context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
                        parseCsv(reader)
                    }
                } else {
                    XlsxSimple.readFirstColumn(context.contentResolver.openInputStream(uri)!!)
                }
                contacts.clear()
                contacts.addAll(imported.distinct().filter { it.isNotBlank() }.map { ContactCall(it) })
                logs.clear()
                message = "${contacts.size} nomor berhasil diimpor."
            } catch (e: Exception) {
                message = "Gagal membaca file: ${e.message ?: "format tidak didukung"}"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)!!.use { out ->
                    XlsxSimple.write(out, contacts.toList())
                }
                message = "Laporan Excel berhasil diekspor."
            } catch (e: Exception) { message = "Export gagal: ${e.message}" }
        }
    }

    LaunchedEffect(running, paused, pendingIndex) {
        if (running && !paused && pendingIndex == -1) {
            delay((interval.toLongOrNull() ?: 10L).coerceIn(3, 3600) * 1000L)
            if (running && !paused && pendingIndex == -1) nextCall()
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF7656C8), secondary = androidx.compose.ui.graphics.Color(0xFFFFB84D))) {
        Scaffold(topBar = {
            TopAppBar(title = {
                Column {
                    Text("🐉 Nailong Auto Dialer", fontWeight = FontWeight.Bold)
                    Text("Kelola panggilan dalam satu daftar", style = MaterialTheme.typography.labelSmall)
                }
            })
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Daftar nomor", fontWeight = FontWeight.Bold)
                        Text("${contacts.size} nomor • ${contacts.count { it.status == "Selesai" }} selesai • ${contacts.count { it.status == "Menunggu" || it.status == "Redial menunggu" }} menunggu")
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importLauncher.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("Import Excel/CSV") }
                    OutlinedButton(onClick = { exportLauncher.launch("Nailong_Laporan.xlsx") }, modifier = Modifier.weight(1f), enabled = contacts.isNotEmpty()) { Text("Export Excel") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(interval, { interval = it.filter(Char::isDigit) }, label = { Text("Interval (detik)") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(maxRedial, { maxRedial = it.filter(Char::isDigit) }, label = { Text("Maks. percobaan") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (contacts.isNotEmpty()) {
                            contacts.forEach { if (it.status == "Selesai" || it.status == "Terjawab") it.status = "Menunggu" }
                            running = true; paused = false; message = "Sesi panggilan dimulai."
                            nextCall()
                        }
                    }, enabled = contacts.isNotEmpty() && !running, modifier = Modifier.weight(1f)) { Text("START") }
                    OutlinedButton(onClick = { paused = true; message = "Dijeda. Tekan RESUME untuk melanjutkan." }, enabled = running && !paused, modifier = Modifier.weight(1f)) { Text("PAUSE") }
                    OutlinedButton(onClick = { paused = false; message = "Dilanjutkan." }, enabled = running && paused, modifier = Modifier.weight(1f)) { Text("RESUME") }
                    OutlinedButton(onClick = { running = false; paused = false; pendingIndex = -1; message = "Sesi dihentikan." }, enabled = running, modifier = Modifier.weight(1f)) { Text("STOP") }
                }
                if (pendingIndex in contacts.indices) {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Panggilan saat ini: ${contacts[pendingIndex].number}", fontWeight = FontWeight.Bold)
                            Text("Setelah panggilan selesai, pilih hasilnya:")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Terjawab", "Tidak dijawab", "Sibuk", "Gagal").forEach { result ->
                                    Button(onClick = {
                                        record(pendingIndex, result)
                                        if (result == "Terjawab") contacts[pendingIndex].status = "Selesai"
                                    }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) { Text(result, style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
                Text("Kontak", fontWeight = FontWeight.Bold)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    itemsIndexed(contacts) { i, c ->
                        Card {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${i + 1}. ${c.number}", fontWeight = FontWeight.SemiBold)
                                    Text("${c.status} • Percobaan: ${c.attempts}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (i == currentIndex) Text("AKTIF", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text("Catatan: panggilan dilakukan melalui dialer Android; pengguna tetap menekan tombol panggil dan menandai hasil secara manual.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun parseCsv(reader: BufferedReader): List<String> {
    val result = mutableListOf<String>()
    reader.lineSequence().forEachIndexed { index, line ->
        val value = line.split(',', ';', '\t').firstOrNull()?.trim()?.trim('"') ?: ""
        val normalized = value.replace(" ", "")
        if (normalized.isNotBlank() && !(index == 0 && normalized.lowercase() in listOf("nomor", "no", "phone", "telephone", "nomor telepon"))) result.add(normalized)
    }
    return result
}
