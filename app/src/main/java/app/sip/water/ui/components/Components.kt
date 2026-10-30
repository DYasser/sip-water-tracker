@file:OptIn(ExperimentalMaterial3Api::class)

package app.sip.water.ui.components

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sip.water.data.Volume
import app.sip.water.data.VolumeUnit
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Clock time in the phone's own 12/24-hour style. */
fun formatTime(context: Context, epochMs: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(epochMs))

fun formatMinuteOfDay(context: Context, minutes: Int): String {
    val ms = LocalDate.now().atTime(LocalTime.of(minutes / 60, minutes % 60))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return formatTime(context, ms)
}

/**
 * The day's progress as a ring. Past 100% it keeps the full ring; the number in
 * the middle is what tells you how far over you went.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "ring",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val start = MaterialTheme.colorScheme.secondary
    val end = MaterialTheme.colorScheme.primary

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (animated > 0f) {
                // Rotate so the gradient's seam sits at 12 o'clock, where the arc starts.
                rotate(-90f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(start, end, start)),
                        startAngle = 0f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/**
 * Add a drink of any size, optionally at an earlier time today — for the glass
 * you forgot to log an hour ago.
 */
@Composable
fun AmountDialog(
    unit: VolumeUnit,
    initialMl: Int,
    onDismiss: () -> Unit,
    onConfirm: (ml: Int, at: Long?) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(Volume.inputValue(initialMl, unit)) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var picking by remember { mutableStateOf(false) }
    val ml = Volume.parse(text, unit)
    val step = if (unit == VolumeUnit.ML) 50 else 30 // ~1 oz

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a drink") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    label = { Text("Amount") },
                    suffix = { Text(Volume.unitLabel(unit)) },
                    singleLine = true,
                    isError = text.isNotEmpty() && ml == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-step, step).forEach { delta ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val next = ((ml ?: 0) + delta).coerceAtLeast(step)
                                text = Volume.inputValue(next, unit)
                            },
                            label = { Text((if (delta > 0) "+" else "−") + Volume.format(step, unit)) },
                        )
                    }
                }
                TextButton(onClick = { picking = true }) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    val picked = time
                    Text(if (picked == null) "Now" else "Today at " + formatMinuteOfDay(context, picked.hour * 60 + picked.minute))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ml != null,
                onClick = {
                    val at = time?.let { t ->
                        val ms = LocalDate.now().atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        // A time later today than now makes no sense; treat it as now.
                        ms.coerceAtMost(System.currentTimeMillis())
                    }
                    onConfirm(ml!!, at)
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        val now = LocalTime.now()
        TimeDialog(
            title = "When did you drink it?",
            initialMinutes = (time ?: now).let { it.hour * 60 + it.minute },
            onDismiss = { picking = false },
            onConfirm = { minutes ->
                time = LocalTime.of(minutes / 60, minutes % 60)
                picking = false
            },
        )
    }
}

@Composable
fun TimeDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) }
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
