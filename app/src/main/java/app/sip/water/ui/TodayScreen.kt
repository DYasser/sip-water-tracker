@file:OptIn(ExperimentalLayoutApi::class)

package app.sip.water.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sip.water.data.Entry
import app.sip.water.data.Volume
import app.sip.water.ui.components.AmountDialog
import app.sip.water.ui.components.ProgressRing
import app.sip.water.ui.components.formatTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    state: UiState,
    notificationsAllowed: Boolean,
    onEnableNotifications: () -> Unit,
    onAdd: (ml: Int, at: Long?) -> Unit,
    onRemove: (Entry) -> Unit,
    contentPadding: PaddingValues,
) {
    val unit = state.settings.unit
    var custom by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(state) }

        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProgressRing(progress = state.progress, modifier = Modifier.size(250.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            Volume.format(state.totalMl, unit),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "of ${Volume.format(state.goalMl, unit)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${(state.progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        item {
            Text(
                if (state.remainingMl > 0) "${Volume.format(state.remainingMl, unit)} to go"
                else "Goal reached — nicely done 🎉",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The one-tap button: whatever you logged last.
        item {
            Button(
                onClick = { onAdd(state.settings.lastAmountMl, null) },
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add ${Volume.format(state.settings.lastAmountMl, unit)}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.settings.cups.forEach { ml ->
                    AssistChip(
                        onClick = { onAdd(ml, null) },
                        label = { Text(Volume.format(ml, unit)) },
                        leadingIcon = { Icon(Icons.Rounded.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                AssistChip(onClick = { custom = true }, label = { Text("Other…") })
            }
        }

        if (!notificationsAllowed && state.settings.reminder.enabled) {
            item { NotificationsOffCard(onEnableNotifications) }
        }

        item { ReminderLine(state) }

        item {
            Text("Today's log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (state.entries.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Tap the big button when you have a drink.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.entries, key = { it.id }) { entry -> EntryRow(entry, unit, onRemove) }
        }
    }

    if (custom) {
        AmountDialog(
            unit = unit,
            initialMl = state.settings.lastAmountMl,
            onDismiss = { custom = false },
            onConfirm = { ml, at ->
                custom = false
                onAdd(ml, at)
            },
        )
    }
}

@Composable
private fun Header(state: UiState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                state.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = state.currentStreak > 0) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${state.currentStreak} day${if (state.currentStreak == 1) "" else "s"}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderLine(state: UiState) {
    val context = LocalContext.current
    val r = state.settings.reminder
    val next = state.settings.nextReminderAt
    val nextDay = java.time.Instant.ofEpochMilli(next).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val text = when {
        !r.enabled -> "Reminders are off"
        next <= 0 -> "No reminder scheduled"
        // Say so when the next one is not today, or "at 8:00" reads as this morning.
        nextDay == state.today -> "Next reminder at ${formatTime(context, next)}"
        state.remainingMl == 0 && r.skipWhenMet -> "Goal met — reminders resume tomorrow at ${formatTime(context, next)}"
        nextDay == state.today.plusDays(1) -> "Next reminder tomorrow at ${formatTime(context, next)}"
        else -> "Next reminder ${nextDay.format(DateTimeFormatter.ofPattern("EEE d MMM"))} at ${formatTime(context, next)}"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (r.enabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NotificationsOffCard(onEnable: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Reminders can't reach you", fontWeight = FontWeight.SemiBold)
            Text(
                "Notifications are turned off for Sip, so reminders stay silent.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onEnable, contentPadding = PaddingValues(0.dp)) { Text("Turn on notifications") }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, unit: app.sip.water.data.VolumeUnit, onRemove: (Entry) -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(
                Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(Volume.format(entry.amountMl, unit), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(formatTime(context, entry.timestamp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { onRemove(entry) }) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove this drink")
            }
        }
    }
}
