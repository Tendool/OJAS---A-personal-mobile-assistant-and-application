package com.ojas.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.ui.theme.AuroraTeal
import com.ojas.assistant.ui.theme.StarlightFaint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * A date and a time as two tappable slabs.
 *
 * The date picker works in UTC-midnight millis (what Material hands back), so the value
 * is converted through LocalDate rather than by adding offsets, which is what keeps the
 * selection correct on either side of a daylight-saving boundary.
 */
@Composable
fun DateTimeField(
    millis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "When"
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val dateTime = remember(millis) { TimeUtils.localDateTime(millis) }

    androidx.compose.foundation.layout.Column(modifier) {
        FieldLabel(label)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Slab(
                text = TimeUtils.formatDay(millis),
                modifier = Modifier.weight(1.4f),
                onClick = { showDate = true }
            )
            Slab(
                text = TimeUtils.formatTime(millis, use24h = true),
                modifier = Modifier.weight(1f),
                onClick = { showTime = true }
            )
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = Color(0xF2080E1C)
            ),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(TimeUtils.toMillis(LocalDateTime.of(date, dateTime.toLocalTime())))
                    }
                    showDate = false
                }) { Text("Set", color = AuroraTeal) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text("Cancel", color = StarlightFaint)
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = Color(0xF2080E1C),
            confirmButton = {
                TextButton(onClick = {
                    val date: LocalDate = dateTime.toLocalDate()
                    onChange(
                        TimeUtils.toMillis(
                            LocalDateTime.of(date, LocalTime.of(state.hour, state.minute))
                        )
                    )
                    showTime = false
                }) { Text("Set", color = AuroraTeal) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text("Cancel", color = StarlightFaint)
                }
            },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
private fun Slab(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x4D0A1224))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
