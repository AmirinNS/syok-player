package io.github.amirinns.syok.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amirinns.syok.data.FolderEntity
import io.github.amirinns.syok.radio.StationEntity
import io.github.amirinns.syok.radio.StationValidator
import io.github.amirinns.syok.ui.theme.SyokAmberAccent
import io.github.amirinns.syok.ui.theme.SyokAmberContrast
import io.github.amirinns.syok.ui.theme.SyokBorder
import io.github.amirinns.syok.ui.theme.SyokNightBase
import io.github.amirinns.syok.ui.theme.SyokNightCard
import io.github.amirinns.syok.ui.theme.SyokNightSurface
import io.github.amirinns.syok.ui.theme.SyokTextHigh
import io.github.amirinns.syok.ui.theme.SyokTextMed

@Composable
fun SourcesScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    LaunchedEffect(viewModel) { viewModel.refreshFolders() }
    val folders by viewModel.folders.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val busy by viewModel.folderBusy.collectAsState()
    val message by viewModel.folderMessage.collectAsState()
    var replacing by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var isAddingStation by rememberSaveable { mutableStateOf(false) }
    var editingStation by remember { mutableStateOf<StationEntity?>(null) }
    var deletingStation by remember { mutableStateOf<StationEntity?>(null) }
    BackHandler(onBack = onBack)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            viewModel.addFolder(uri, result.data?.flags ?: 0, replacing?.let(Uri::parse))
        }
        replacing = null
    }
    fun chooseFolder(folder: FolderEntity? = null) {
        replacing = folder?.treeUri
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && folder != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(folder.treeUri))
            }
        }
        try {
            picker.launch(intent)
        } catch (_: ActivityNotFoundException) {
            replacing = null
            viewModel.reportFolderError("This device has no folder picker. A working document provider is needed for USB/SD access.")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SyokNightBase),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SourceAction("Library", onClick = onBack)
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text("Sources", color = SyokTextHigh, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Set up while parked", color = SyokAmberAccent, fontSize = 16.sp)
                }
            }
        }
        item {
            SourceAction("Add folder", enabled = !busy, primary = true, modifier = Modifier.fillMaxWidth()) {
                chooseFolder()
            }
        }
        item {
            Text(
                message ?: "Choose a USB, SD, or device music folder. Syok keeps read access after restart.",
                color = SyokTextMed, fontSize = 16.sp
            )
        }
        if (folders.isEmpty()) {
            item {
                Text("No folders added. Connect a drive, then tap Add folder.", color = SyokTextHigh, fontSize = 16.sp)
            }
        }
        items(folders, key = { it.treeUri }) { folder ->
            Column(
                Modifier.fillMaxWidth().background(SyokNightSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, SyokBorder, RoundedCornerShape(12.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(folder.name, color = SyokTextHigh, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                val status = when (folder.status) {
                    FolderEntity.AVAILABLE -> "Available"
                    FolderEntity.ACCESS_REVOKED -> "Access revoked"
                    else -> "Unavailable"
                }
                Text(status, color = SyokAmberAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(folder.detail, color = SyokTextMed, fontSize = 16.sp)
                BoxWithConstraints {
                    @Composable
                    fun actions(modifier: Modifier) {
                        SourceAction("Reconnect / reselect", !busy, modifier = modifier,
                            description = "Reconnect or reselect ${folder.name}") { chooseFolder(folder) }
                        SourceAction("Retry indexing", !busy, modifier = modifier,
                            description = "Retry indexing ${folder.name}") { viewModel.retryFolder(folder) }
                        SourceAction("Remove folder", !busy, modifier = modifier,
                            description = "Remove ${folder.name}") { removing = folder.treeUri }
                    }
                    if (maxWidth < 600.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { actions(Modifier.fillMaxWidth()) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // Stations Section
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text("Stations", color = SyokTextHigh, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Internet radio · Set up while parked", color = SyokAmberAccent, fontSize = 16.sp)
                }
            }
        }
        item {
            SourceAction("Add station", primary = true, modifier = Modifier.fillMaxWidth()) {
                isAddingStation = true
            }
        }
        if (stations.isEmpty()) {
            item {
                Text("No radio stations added. Tap Add station to save an HTTPS stream or HLS URL.", color = SyokTextMed, fontSize = 16.sp)
            }
        }
        items(stations, key = { it.id }) { station ->
            Column(
                Modifier.fillMaxWidth().background(SyokNightSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, SyokBorder, RoundedCornerShape(12.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(station.name, color = SyokTextHigh, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("HTTPS STREAM", color = SyokAmberAccent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(station.url, color = SyokTextMed, fontSize = 15.sp)
                BoxWithConstraints {
                    @Composable
                    fun stationActions(modifier: Modifier) {
                        SourceAction("Edit station", modifier = modifier,
                            description = "Edit ${station.name}") { editingStation = station }
                        SourceAction("Delete station", modifier = modifier,
                            description = "Delete ${station.name}") { deletingStation = station }
                    }
                    if (maxWidth < 600.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { stationActions(Modifier.fillMaxWidth()) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { stationActions(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    folders.firstOrNull { it.treeUri == removing }?.let { folder ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${folder.name}?") },
            text = { Text("Syok will release folder access and remove its tracks from playback. Your original files stay on the drive.") },
            confirmButton = {
                SourceAction("Remove folder") {
                    removing = null
                    viewModel.removeFolder(folder)
                }
            },
            dismissButton = { SourceAction("Keep folder") { removing = null } }
        )
    }

    if (isAddingStation || editingStation != null) {
        val target = editingStation
        StationFormDialog(
            station = target,
            onDismiss = {
                isAddingStation = false
                editingStation = null
            },
            onSave = { name, url ->
                if (target != null) {
                    viewModel.updateStation(target.id, name, url)
                } else {
                    viewModel.addStation(name, url)
                }
                isAddingStation = false
                editingStation = null
            }
        )
    }

    deletingStation?.let { station ->
        AlertDialog(
            onDismissRequest = { deletingStation = null },
            title = { Text("Delete ${station.name}?") },
            text = { Text("Syok will remove this station from your saved stations.") },
            confirmButton = {
                SourceAction("Delete station") {
                    deletingStation = null
                    viewModel.deleteStation(station)
                }
            },
            dismissButton = { SourceAction("Keep station") { deletingStation = null } }
        )
    }
}

@Composable
private fun StationFormDialog(
    station: StationEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(station?.name ?: "") }
    var url by rememberSaveable { mutableStateOf(station?.url ?: "") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var urlError by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (station != null) "Edit Station" else "Add Station",
                color = SyokTextHigh,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Set up while parked. Enter an HTTPS direct stream (MP3/AAC) or HLS (.m3u8) URL.",
                    color = SyokAmberAccent,
                    fontSize = 14.sp
                )

                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                        },
                        label = { Text("Station Name") },
                        isError = nameError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SyokTextHigh,
                            unfocusedTextColor = SyokTextHigh,
                            focusedContainerColor = SyokNightCard,
                            unfocusedContainerColor = SyokNightCard,
                            focusedBorderColor = SyokAmberAccent,
                            unfocusedBorderColor = SyokBorder,
                            errorBorderColor = SyokAmberAccent,
                            focusedLabelColor = SyokAmberAccent,
                            unfocusedLabelColor = SyokTextMed
                        )
                    )
                    if (nameError != null) {
                        Text(
                            text = nameError!!,
                            color = SyokAmberAccent,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            urlError = null
                        },
                        label = { Text("Stream URL (HTTPS only)") },
                        isError = urlError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SyokTextHigh,
                            unfocusedTextColor = SyokTextHigh,
                            focusedContainerColor = SyokNightCard,
                            unfocusedContainerColor = SyokNightCard,
                            focusedBorderColor = SyokAmberAccent,
                            unfocusedBorderColor = SyokBorder,
                            errorBorderColor = SyokAmberAccent,
                            focusedLabelColor = SyokAmberAccent,
                            unfocusedLabelColor = SyokTextMed
                        )
                    )
                    if (urlError != null) {
                        Text(
                            text = urlError!!,
                            color = SyokAmberAccent,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            SourceAction("Save station", primary = true) {
                val nameRes = StationValidator.validateName(name)
                val urlRes = StationValidator.validateUrl(url)
                var hasError = false
                if (nameRes is StationValidator.Result.Invalid) {
                    nameError = nameRes.reason
                    hasError = true
                }
                if (urlRes is StationValidator.Result.Invalid) {
                    urlError = urlRes.reason
                    hasError = true
                }
                if (!hasError) {
                    onSave(name.trim(), url.trim())
                }
            }
        },
        dismissButton = {
            SourceAction("Cancel", onClick = onDismiss)
        }
    )
}

@Composable
fun FolderRecoveryBanner(message: String, onSources: () -> Unit) {
    SourceAction(
        "$message Open Sources", primary = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onSources
    )
}

@Composable
private fun SourceAction(
    text: String,
    enabled: Boolean = true,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    description: String = text,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 76.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) SyokAmberAccent else SyokNightCard,
            contentColor = if (primary) SyokAmberContrast else SyokTextHigh
        )
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
