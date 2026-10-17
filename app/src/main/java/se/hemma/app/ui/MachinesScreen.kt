@file:OptIn(ExperimentalMaterial3Api::class)

package se.hemma.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.hemma.app.data.Defaults
import se.hemma.app.data.Machine
import se.hemma.app.data.MachineType
import se.hemma.app.domain.Fmt
import java.util.UUID

@Composable
fun MachinesScreen(
    machines: List<Machine>,
    onSave: (Machine) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    var editing by remember { mutableStateOf<Machine?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Machine?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Lägg in varje maskin eller program med energi per körning. Värdena finns i " +
                        "bruksanvisningen – på energimärkningen anges ofta kWh per 100 cykler, dela då med 100.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            itemsIndexed(machines, key = { _, m -> m.id }) { index, m ->
                MachineRow(
                    machine = m,
                    canMoveUp = index > 0,
                    canMoveDown = index < machines.size - 1,
                    onEdit = { editing = m },
                    onDelete = { deleting = m },
                    onMove = { delta -> onMove(m.id, delta) },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Lägg till") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (creating || editing != null) {
        MachineEditorDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = {
                onSave(it)
                creating = false
                editing = null
            },
        )
    }

    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Ta bort ${m.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(m.id)
                    deleting = null
                }) { Text("Ta bort") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Avbryt") } },
        )
    }
}

@Composable
private fun MachineRow(
    machine: Machine,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    HemmaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(machine.type.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(machine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (machine.model.isNotBlank()) {
                    Text(machine.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(describe(machine), style = MaterialTheme.typography.bodySmall)
            }
            Column {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Flytta upp")
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Flytta ner")
                }
            }
            Column {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Redigera") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Ta bort") }
            }
        }
    }
}

private fun describe(m: Machine): String = buildString {
    if (m.isCar) {
        append("${Fmt.num(m.energyKwh, 1)} kWh till batteriet · ${Fmt.num(m.powerKw ?: 0.0, 1)} kW · ca ${Fmt.duration(m.runMinutes())}")
    } else {
        append("${Fmt.num(m.energyKwh, 2)} kWh · ${Fmt.duration(m.runMinutes())}")
    }
    m.readyByHour?.let { append(" · klar kl ${"%02d".format(it)}") }
    if (m.showOnWidget) append(" · i widget")
}

@Composable
private fun MachineEditorDialog(
    initial: Machine?,
    onDismiss: () -> Unit,
    onSave: (Machine) -> Unit,
) {
    val start = initial ?: Defaults.forType(MachineType.WASHER).let { (kwh, min, _) ->
        Machine(id = "", name = MachineType.WASHER.label, type = MachineType.WASHER, energyKwh = kwh, durationMinutes = min)
    }
    var type by remember { mutableStateOf(start.type) }
    var name by remember { mutableStateOf(start.name) }
    var model by remember { mutableStateOf(start.model) }
    var energy by remember { mutableStateOf(Fmt.input(start.energyKwh)) }
    var minutes by remember { mutableStateOf(if (start.durationMinutes > 0) start.durationMinutes.toString() else "60") }
    var power by remember { mutableStateOf(start.powerKw?.let { Fmt.input(it) } ?: "11") }
    var readyBy by remember { mutableStateOf(start.readyByHour?.toString() ?: "") }
    var onWidget by remember { mutableStateOf(start.showOnWidget) }

    val e = Fmt.parse(energy)
    val mins = minutes.trim().toIntOrNull()
    val kw = Fmt.parse(power)
    val rb = readyBy.trim().toIntOrNull()
    val isCar = type == MachineType.CAR
    val energyOk = e != null && e > 0
    val timeOk = if (isCar) kw != null && kw > 0 else mins != null && mins > 0
    val readyOk = readyBy.isBlank() || (rb != null && rb in 0..23)
    val valid = name.isNotBlank() && energyOk && timeOk && readyOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Ny maskin" else "Redigera") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MachineType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                if (initial == null && t != type) {
                                    // Ny maskin: fyll i typvärden för vald typ.
                                    val (k, m, p) = Defaults.forType(t)
                                    energy = Fmt.input(k)
                                    if (m > 0) minutes = m.toString()
                                    p?.let { power = Fmt.input(it) }
                                    if (name.isBlank() || name == type.label) name = t.label
                                    if (t == MachineType.CAR && readyBy.isBlank()) readyBy = "7"
                                }
                                type = t
                            },
                            label = { Text("${t.emoji} ${t.label}") },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Namn, t.ex. Tvätt 40°") },
                    singleLine = true,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Modell (valfritt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isCar) {
                    NumberInput(energy, { energy = it }, "Energi att ladda", "kWh", !energyOk)
                    NumberInput(power, { power = it }, "Laddeffekt", "kW", !timeOk)
                    if (e != null && kw != null && kw > 0) {
                        val preview = Machine("", "", MachineType.CAR, energyKwh = e, durationMinutes = 0, powerKw = kw)
                        Text(
                            "≈ ${Fmt.duration(preview.runMinutes())} laddning, ${Fmt.num(preview.gridKwh(), 1)} kWh från elnätet " +
                                "(räknat med 10 % laddförluster).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    NumberInput(energy, { energy = it }, "Energi per körning", "kWh", !energyOk)
                    NumberInput(minutes, { minutes = it }, "Programtid", "min", !timeOk, KeyboardType.Number)
                }
                NumberInput(readyBy, { readyBy = it }, "Klar senast kl (valfritt, 0–23)", "", !readyOk, KeyboardType.Number)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Visa i widgeten", modifier = Modifier.weight(1f))
                    Switch(checked = onWidget, onCheckedChange = { onWidget = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (e != null) onSave(
                        Machine(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            type = type,
                            model = model.trim(),
                            energyKwh = e,
                            durationMinutes = if (isCar) 0 else (mins ?: 60),
                            powerKw = if (isCar) kw else null,
                            readyByHour = if (readyBy.isBlank()) null else rb,
                            showOnWidget = onWidget,
                        ),
                    )
                },
            ) { Text("Spara") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
    )
}

@Composable
private fun NumberInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    isError: Boolean,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { if (suffix.isNotEmpty()) Text(suffix) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
