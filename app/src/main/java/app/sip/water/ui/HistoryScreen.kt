@file:OptIn(ExperimentalMaterial3Api::class)

package app.sip.water.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sip.water.data.Volume
import app.sip.water.data.VolumeUnit
import app.sip.water.ui.components.formatTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(state: UiState, contentPadding: PaddingValues) {
    val unit = state.settings.unit
    var range by remember { mutableStateOf(7) }
    var expanded by remember { mutableStateOf<Long?>(null) }

    // Only the days since you started: empty days before your first drink are just noise.
    val started = state.days.filter { d -> state.firstDay != null && !d.date.isBefore(state.firstDay) }
    // Today is still in progress, so the average uses completed days only —
    // unless today is your very first day, when it is all there is.
    val completed7 = started.takeLast(8).filter { it.date != state.today }.takeLast(7)
    val avg7 = if (completed7.isNotEmpty()) completed7.sumOf { it.totalMl } / completed7.size else state.totalMl
    val met30 = started.count { it.met }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Icons.Rounded.LocalFireDepartment, "Current streak", days(state.currentStreak), Modifier.weight(1f))
                StatCard(Icons.Rounded.EmojiEvents, "Best streak", days(state.bestStreak), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Icons.Rounded.TaskAlt, "Goal hit (30 days)", "$met30 / ${started.size.coerceAtLeast(0)}", Modifier.weight(1f))
                StatCard(Icons.Rounded.WaterDrop, "Daily average (7 days)", Volume.format(avg7, unit), Modifier.weight(1f))
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(7, 30).forEachIndexed { i, n ->
                            SegmentedButton(
                                selected = range == n,
                                onClick = { range = n },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text("$n days") }
                        }
                    }
                    BarChart(state.days.takeLast(range))
                    Text(
                        "Dashed line: your daily goal. Solid bars: goal met.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text("Recent days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (started.isEmpty()) {
            item {
                Text(
                    "Your days will show up here once you log your first drink.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(started.reversed(), key = { it.date.toEpochDay() }) { day ->
            val key = day.date.toEpochDay()
            DayRow(
                day = day,
                isToday = day.date == state.today,
                unit = unit,
                entries = state.entriesByDay[key].orEmpty(),
                expanded = expanded == key,
                onToggle = { expanded = if (expanded == key) null else key },
            )
        }
    }
}

private fun days(n: Int) = "$n day${if (n == 1) "" else "s"}"

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarChart(days: List<DayStat>) {
    if (days.isEmpty()) return
    val met = MaterialTheme.colorScheme.primary
    val notMet = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val goalLine = MaterialTheme.colorScheme.tertiary
    val maxValue = days.maxOf { maxOf(it.totalMl, it.goalMl) }.coerceAtLeast(1)

    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val n = days.size
        val gap = if (n > 10) 3.dp.toPx() else 10.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        val radius = minOf(barW / 2, 6.dp.toPx())
        days.forEachIndexed { i, d ->
            val x = i * (barW + gap)
            val h = size.height * d.totalMl / maxValue
            if (d.totalMl == 0) {
                val stub = 3.dp.toPx()
                drawRoundRect(empty, Offset(x, size.height - stub), Size(barW, stub), CornerRadius(radius, radius))
            } else {
                drawRoundRect(
                    color = if (d.met) met else notMet,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
        // The current goal as a dashed line.
        val y = size.height * (1 - days.last().goalMl.toFloat() / maxValue)
        drawLine(
            color = goalLine,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
        )
    }

    Row(Modifier.fillMaxWidth()) {
        days.forEachIndexed { i, d ->
            val label = when {
                days.size <= 10 -> d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                // Count back from today in whole weeks, so two labels are never side by side.
                (days.lastIndex - i) % 7 == 0 -> d.date.dayOfMonth.toString()
                else -> ""
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayRow(
    day: DayStat,
    isToday: Boolean,
    unit: VolumeUnit,
    entries: List<app.sip.water.data.Entry>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onToggle),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isToday) "Today" else day.date.format(DateTimeFormatter.ofPattern("EEE d MMM")),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${Volume.format(day.totalMl, unit)} / ${Volume.format(day.goalMl, unit)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (day.met) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Goal met",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { day.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                drawStopIndicator = {},
            )
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                if (entries.isEmpty()) {
                    Text("No drinks logged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    entries.sortedBy { it.timestamp }.forEach { e ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text(formatTime(context, e.timestamp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
                            Text(Volume.format(e.amountMl, unit))
                        }
                    }
                }
            }
        }
    }
}
