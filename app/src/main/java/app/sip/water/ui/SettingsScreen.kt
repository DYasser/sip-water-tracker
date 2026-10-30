@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.sip.water.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.sip.water.BuildConfig
import app.sip.water.data.ReminderConfig
import app.sip.water.data.Stats
import app.sip.water.data.Volume
import app.sip.water.data.VolumeUnit
import app.sip.water.reminders.Notifications
import app.sip.water.reminders.ReminderCheck
import app.sip.water.ui.components.TimeDialog
import app.sip.water.ui.components.formatMinuteOfDay
import java.time.LocalDate

private enum class Dialog { Goal, AddCup, Interval, Start, End, Restore, DeleteAll, Check }

@Composable
fun SettingsScreen(vm: MainViewModel, state: UiState, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val s = state.settings
    val r = s.reminder
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    // Re-read on return from system settings, where these can change.
    var ignoringBattery by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val pm = context.getSystemService(PowerManager::class.java)
        ignoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::backup)
    }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestore = uri
            dialog = Dialog.Restore
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        // ---- Goal & units ----
        item { Section("Goal") }
        item {
            Row_(
                title = "Daily goal",
                subtitle = Volume.format(s.goalMl, s.unit),
                onClick = { dialog = Dialog.Goal },
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Units", style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    VolumeUnit.entries.forEachIndexed { i, u ->
                        SegmentedButton(
                            selected = s.unit == u,
                            onClick = { vm.setUnit(u) },
                            shape = SegmentedButtonDefaults.itemShape(i, VolumeUnit.entries.size),
                        ) { Text(if (u == VolumeUnit.ML) "Millilitres" else "Fluid ounces") }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Quick-add sizes", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "The buttons under the big one on the Today screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    s.cups.forEach { ml ->
                        InputChip(
                            selected = false,
                            onClick = { if (s.cups.size > 1) vm.setCups(s.cups - ml) },
                            label = { Text(Volume.format(ml, s.unit)) },
                            trailingIcon = {
                                if (s.cups.size > 1) Icon(Icons.Rounded.Close, contentDescription = "Remove", Modifier.size(16.dp))
                            },
                        )
                    }
                    InputChip(
                        selected = false,
                        onClick = { dialog = Dialog.AddCup },
                        label = { Text("Add") },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(16.dp)) },
                    )
                }
            }
        }

        // ---- Reminders ----
        item { Section("Reminders") }
        item {
            SwitchRow("Remind me to drink", "Vibrates, and stays quiet outside your active hours", r.enabled) { on ->
                vm.updateReminders { it.copy(enabled = on) }
            }
        }
        if (r.enabled) {
            item { Row_("How often", "Every ${intervalLabel(r.intervalMin)}", onClick = { dialog = Dialog.Interval }) }
            item {
                Row_(
                    "Active hours",
                    "${formatMinuteOfDay(context, r.startMin)} – ${formatMinuteOfDay(context, r.endMin)}",
                    onClick = { dialog = Dialog.Start },
                )
            }
            item {
                SwitchRow("Play a sound", "Off: vibration only", r.sound) { on ->
                    vm.updateReminders { it.copy(sound = on) }
                }
            }
            item {
                SwitchRow("Count from my last drink", "Drinking pushes the next reminder back", r.smart) { on ->
                    vm.updateReminders { it.copy(smart = on) }
                }
            }
            item {
                SwitchRow("Stop once the goal is met", "No more reminders for the rest of the day", r.skipWhenMet) { on ->
                    vm.updateReminders { it.copy(skipWhenMet = on) }
                }
            }
            item {
                Row_("Send a test reminder", "Feel what a reminder is like, and check nothing is muting it", onClick = {
                    vm.sendTestReminder()
                    dialog = Dialog.Check
                })
            }
            item {
                Row_("Sound & vibration settings", "Fine-tune in Android's notification settings", onClick = {
                    context.startActivity(
                        Intent(SystemSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(
                                SystemSettings.EXTRA_CHANNEL_ID,
                                if (r.sound) Notifications.CHANNEL_SOUND else Notifications.CHANNEL_VIBRATE,
                            ),
                    )
                })
            }
            if (!ignoringBattery) {
                item {
                    Row_(
                        "Make reminders reliable",
                        "Some phones delay alarms to save battery. Tap to let Sip run on time.",
                        warning = true,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                    )
                }
            }
        }

        // ---- Appearance ----
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item { Section("Appearance") }
            item {
                SwitchRow("Match my wallpaper colours", "Use Android's Material You palette instead of blue", s.dynamicColor) {
                    vm.setDynamicColor(it)
                }
            }
        }

        // ---- Data ----
        item { Section("Your data") }
        item {
            Row_("Back up", "Save everything to a file — use it to move to a new phone", onClick = {
                backup.launch("sip-backup-${LocalDate.now()}.json")
            })
        }
        item { Row_("Restore from backup", "Replaces what is on this phone", onClick = { restore.launch(arrayOf("application/json", "*/*")) }) }
        item {
            Row_("Export as CSV", "Open your log in a spreadsheet", onClick = {
                csv.launch("sip-log-${LocalDate.now()}.csv")
            })
        }
        item { Row_("Delete all drinks", "Your settings are kept", warning = true, onClick = { dialog = Dialog.DeleteAll }) }

        item { Section("About") }
        item {
            Text(
                "Sip ${BuildConfig.VERSION_NAME}\nNo ads, no accounts, no internet access. Everything stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }

    when (dialog) {
        Dialog.Goal -> GoalDialog(s.goalMl, s.unit, onDismiss = { dialog = null }) { goal ->
            vm.setGoal(goal)
            dialog = null
        }
        Dialog.AddCup -> AmountDialogForCup(s.unit, onDismiss = { dialog = null }) { ml ->
            vm.setCups(s.cups + ml)
            dialog = null
        }
        Dialog.Interval -> IntervalDialog(r.intervalMin, onDismiss = { dialog = null }) { minutes ->
            vm.updateReminders { it.copy(intervalMin = minutes) }
            dialog = null
        }
        // Active hours are two pickers in a row: start, then end.
        Dialog.Start -> TimeDialog("Remind me from", r.startMin, onDismiss = { dialog = null }) { m ->
            vm.updateReminders { it.copy(startMin = m) }
            dialog = Dialog.End
        }
        Dialog.End -> TimeDialog("…until", r.endMin, onDismiss = { dialog = null }) { m ->
            vm.updateReminders { it.copy(endMin = m) }
            dialog = null
        }
        Dialog.Restore -> ConfirmDialog(
            title = "Restore this backup?",
            text = "Every drink on this phone will be replaced by the ones in the backup.",
            confirm = "Restore",
            onDismiss = { dialog = null },
        ) {
            pendingRestore?.let(vm::restore)
            dialog = null
        }
        Dialog.Check -> ReminderCheckDialog(
            soundWanted = r.sound,
            onTurnOnSound = { vm.updateReminders { it.copy(sound = true) } },
            onDismiss = { dialog = null },
        )
        Dialog.DeleteAll -> ConfirmDialog(
            title = "Delete every drink?",
            text = "Your whole history and streaks go with it. This cannot be undone — back up first if unsure.",
            confirm = "Delete",
            onDismiss = { dialog = null },
        ) {
            vm.deleteAll()
            dialog = null
        }
        null -> Unit
    }
}

/**
 * Shown right after a test reminder: what on this phone could stop it from
 * buzzing or ringing, each with a button to the settings page that fixes it.
 * Re-checked whenever you come back from one of those pages.
 */
@Composable
private fun ReminderCheckDialog(soundWanted: Boolean, onTurnOnSound: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var findings by remember(soundWanted) { mutableStateOf(ReminderCheck.run(context, soundWanted)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { findings = ReminderCheck.run(context, soundWanted) }

    fun open(fix: ReminderCheck.Fix) {
        val pkg = context.packageName
        val intent = when (fix) {
            ReminderCheck.Fix.AppNotifications ->
                Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE, pkg)
            ReminderCheck.Fix.Channel ->
                Intent(SystemSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(SystemSettings.EXTRA_APP_PACKAGE, pkg)
                    .putExtra(
                        SystemSettings.EXTRA_CHANNEL_ID,
                        if (soundWanted) Notifications.CHANNEL_SOUND else Notifications.CHANNEL_VIBRATE,
                    )
            ReminderCheck.Fix.DoNotDisturb -> Intent("android.settings.ZEN_MODE_SETTINGS")
            ReminderCheck.Fix.PhoneSound -> Intent(SystemSettings.ACTION_SOUND_SETTINGS)
            ReminderCheck.Fix.TurnOnAppSound -> {
                onTurnOnSound()
                return
            }
        }
        // Some phones rename or hide these pages; fall back to the general sound settings.
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(Intent(SystemSettings.ACTION_SOUND_SETTINGS)) } }
    }

    // "Sound is off in Sip" and maker hints are informational; the rest actually block the alert.
    val blocking = findings.filter {
        it.fix != ReminderCheck.Fix.TurnOnAppSound && !it.title.endsWith("may mute new apps")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test reminder sent") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (blocking.isEmpty()) {
                        "Nothing on this phone is muting Sip. It should have vibrated" +
                            (if (soundWanted) " and played a sound." else ".")
                    } else {
                        "Didn't feel it? This is what is muting it:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                findings.forEach { f ->
                    Column {
                        Text(f.title, fontWeight = FontWeight.SemiBold)
                        Text(f.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        f.fix?.let { fix ->
                            TextButton(onClick = { open(fix) }, contentPadding = PaddingValues(0.dp)) {
                                Text(
                                    when (fix) {
                                        ReminderCheck.Fix.TurnOnAppSound -> "Turn on sound"
                                        ReminderCheck.Fix.DoNotDisturb -> "Open Do Not Disturb"
                                        ReminderCheck.Fix.PhoneSound -> "Open sound settings"
                                        else -> "Open Sip's notification settings"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun intervalLabel(minutes: Int) = when {
    minutes < 60 -> "$minutes minutes"
    minutes % 60 == 0 -> if (minutes == 60) "hour" else "${minutes / 60} hours"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

@Composable
private fun Section(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Row_(title: String, subtitle: String? = null, warning: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = if (warning) MaterialTheme.colorScheme.error else Color.Unspecified) },
        supportingContent = subtitle?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) })
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun GoalDialog(currentMl: Int, unit: VolumeUnit, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(Volume.inputValue(currentMl, unit)) }
    var weight by remember { mutableStateOf("") }
    val goal = Volume.parse(text, unit)
    // Weight follows the volume unit: kilograms with ml, pounds with ounces.
    val weightKg = weight.replace(',', '.').toDoubleOrNull()?.let { if (unit == VolumeUnit.ML) it else it * 0.4536 }
    val suggestion = weightKg?.takeIf { it in 20.0..300.0 }?.let(Stats::suggestedGoalMl)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    label = { Text("Goal") },
                    suffix = { Text(Volume.unitLabel(unit)) },
                    singleLine = true,
                    isError = text.isNotEmpty() && goal == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Not sure? A common rule of thumb is about 35 ml per kg of body weight.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = weight,
                    onValueChange = { v -> weight = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
                    label = { Text("Your weight") },
                    suffix = { Text(if (unit == VolumeUnit.ML) "kg" else "lb") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestion != null) {
                    TextButton(onClick = { text = Volume.inputValue(suggestion, unit) }) {
                        Text("Use ${Volume.format(suggestion, unit)}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = goal != null && goal in 250..10_000, onClick = { onConfirm(goal!!) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AmountDialogForCup(unit: VolumeUnit, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val ml = Volume.parse(text, unit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New quick-add size") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                label = { Text("Size") },
                suffix = { Text(Volume.unitLabel(unit)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = ml != null, onClick = { onConfirm(ml!!) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IntervalDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val options = listOf(30, 45, 60, 90, 120, 180)
    var picked by remember { mutableStateOf(if (current in options) current else 60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me every") },
        text = {
            Column {
                options.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = picked == m, role = Role.RadioButton, onClick = { picked = m })
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = picked == m, onClick = null)
                        Text("Every ${intervalLabel(m)}", Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(picked) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
