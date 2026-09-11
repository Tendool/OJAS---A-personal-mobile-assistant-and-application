package com.ojas.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.entity.AlarmEntity
import com.ojas.assistant.data.local.entity.RepeatRule
import com.ojas.assistant.data.local.entity.ReminderEntity
import com.ojas.assistant.ui.components.EmptyState
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OjasScreen
import com.ojas.assistant.ui.components.OrbitIconButton
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.navigation.Routes
import com.ojas.assistant.ui.theme.AuroraTeal
import com.ojas.assistant.ui.theme.PulsarRose
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import com.ojas.assistant.ui.viewmodel.ScheduleViewModel

private val tabTitles = listOf("Alarms", "Reminders", "Calendar")

@Composable
fun ScheduleScreen(
    initialTab: Int,
    contentPadding: PaddingValues
) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, tabTitles.lastIndex)) }

    when (tab) {
        Routes.TAB_CALENDAR -> CalendarTab(
            contentPadding = contentPadding,
            tabIndex = tab,
            onTabChange = { tab = it }
        )
        else -> AlarmsAndReminders(
            contentPadding = contentPadding,
            tabIndex = tab,
            onTabChange = { tab = it }
        )
    }
}

/** Segmented control shared by all three tabs so the header never shifts between them. */
@Composable
internal fun ScheduleTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x4D0A1224))
            .border(1.dp, SpaceOutlineSoft, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabTitles.forEachIndexed { index, title ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) AuroraTeal.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) AuroraTeal else StarlightFaint
                )
            }
        }
    }
}

@Composable
private fun AlarmsAndReminders(
    contentPadding: PaddingValues,
    tabIndex: Int,
    onTabChange: (Int) -> Unit
) {
    val container = LocalOjas.current
    val viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }

    val alarmsTab = tabIndex == Routes.TAB_ALARMS

    OjasScreen(
        title = "Plan",
        subtitle = if (alarmsTab) {
            "${state.alarms.count { it.enabled }} alarms armed"
        } else {
            "${state.reminders.count { it.enabled }} reminders waiting"
        },
        contentPadding = contentPadding,
        actions = {
            OrbitIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = if (alarmsTab) "New alarm" else "New reminder",
                onClick = {
                    if (alarmsTab) {
                        editingAlarm = AlarmEntity(hour = 7, minute = 0)
                    } else {
                        editingReminder = ReminderEntity(
                            title = "",
                            triggerAt = TimeUtils.now() + 60 * 60_000L
                        )
                    }
                },
                tint = AuroraTeal
            )
        }
    ) {
        item(key = "tabs") { ScheduleTabs(selected = tabIndex, onSelect = onTabChange) }

        if (!state.canScheduleExact) {
            item(key = "exact-warning") {
                OjasCard(borderColor = PulsarRose.copy(alpha = 0.45f)) {
                    Text(
                        "Exact alarms are switched off",
                        style = MaterialTheme.typography.titleMedium,
                        color = PulsarRose
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Android will still wake the device, but it may batch the delivery by a " +
                            "few minutes. Grant exact alarms in Settings for to-the-second timing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StarlightDim
                    )
                }
            }
        }

        if (alarmsTab) {
            if (state.alarms.isEmpty()) {
                item(key = "alarms-empty") {
                    OjasCard {
                        EmptyState(
                            title = "No alarms",
                            body = "Add one with the plus, or say \"set an alarm for 6:30 am\"."
                        )
                    }
                }
            } else {
                items(state.alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        use24h = state.settings.use24h,
                        nextTrigger = viewModel.nextTriggerFor(alarm),
                        onToggle = { viewModel.toggleAlarm(alarm.id, it) },
                        onClick = { editingAlarm = alarm },
                        onSkip = { viewModel.skipNextAlarm(alarm.id) }
                    )
                }
            }
        } else {
            if (state.reminders.isEmpty()) {
                item(key = "reminders-empty") {
                    OjasCard {
                        EmptyState(
                            title = "Nothing to remember",
                            body = "Try \"remind me to stretch in 20 minutes\"."
                        )
                    }
                }
            } else {
                items(state.reminders, key = { it.id }) { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        use24h = state.settings.use24h,
                        onToggle = { viewModel.toggleReminder(reminder.id, it) },
                        onClick = { editingReminder = reminder }
                    )
                }
            }
        }
    }

    editingAlarm?.let { alarm ->
        AlarmEditor(
            alarm = alarm,
            onDismiss = { editingAlarm = null },
            onSave = {
                viewModel.saveAlarm(it)
                editingAlarm = null
            },
            onDelete = if (alarm.id == 0L) null else {
                {
                    viewModel.deleteAlarm(alarm.id)
                    editingAlarm = null
                }
            }
        )
    }

    editingReminder?.let { reminder ->
        ReminderEditor(
            reminder = reminder,
            onDismiss = { editingReminder = null },
            onSave = {
                viewModel.saveReminder(it)
                editingReminder = null
            },
            onDelete = if (reminder.id == 0L) null else {
                {
                    viewModel.deleteReminder(reminder.id)
                    editingReminder = null
                }
            }
        )
    }
}

// ------------------------------------------------------------------- rows

@Composable
private fun AlarmRow(
    alarm: AlarmEntity,
    use24h: Boolean,
    nextTrigger: Long,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onSkip: () -> Unit
) {
    OjasCard(contentPadding = PaddingValues(16.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    TimeUtils.formatTime(alarm.hour, alarm.minute, use24h),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light,
                    color = if (alarm.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        StarlightFaint
                    }
                )
                Text(
                    buildString {
                        append(TimeUtils.describeDays(alarm.daysMask))
                        if (alarm.label.isNotBlank()) append("  ·  ${alarm.label}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = StarlightDim
                )
                if (alarm.enabled && nextTrigger > 0L) {
                    Text(
                        "Rings ${TimeUtils.relative(nextTrigger, use24h = use24h)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroraTeal
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AuroraTeal,
                        checkedTrackColor = AuroraTeal.copy(alpha = 0.28f),
                        uncheckedThumbColor = StarlightFaint,
                        uncheckedTrackColor = Color(0x330A1224)
                    )
                )
                if (alarm.enabled && alarm.daysMask != 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "skip next",
                        style = MaterialTheme.typography.labelSmall,
                        color = StarlightFaint,
                        modifier = Modifier.clickable(onClick = onSkip)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderEntity,
    use24h: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    OjasCard(contentPadding = PaddingValues(16.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    reminder.title.ifBlank { "Reminder" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (reminder.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        StarlightFaint
                    }
                )
                if (reminder.note.isNotBlank()) {
                    Text(
                        reminder.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StarlightDim
                    )
                }
                Text(
                    "${TimeUtils.formatDay(reminder.triggerAt)} · " +
                        "${TimeUtils.formatTime(reminder.triggerAt, use24h)} · ${reminder.repeat.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reminder.enabled) AuroraTeal else StarlightFaint
                )
            }
            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AuroraTeal,
                    checkedTrackColor = AuroraTeal.copy(alpha = 0.28f),
                    uncheckedThumbColor = StarlightFaint,
                    uncheckedTrackColor = Color(0x330A1224)
                )
            )
        }
    }
}

// ---------------------------------------------------------------- editors

@Composable
private fun AlarmEditor(
    alarm: AlarmEntity,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var hour by remember(alarm.id) { mutableIntStateOf(alarm.hour) }
    var minute by remember(alarm.id) { mutableIntStateOf(alarm.minute) }
    var label by remember(alarm.id) { mutableStateOf(alarm.label) }
    var daysMask by remember(alarm.id) { mutableIntStateOf(alarm.daysMask) }
    var vibrate by remember(alarm.id) { mutableStateOf(alarm.vibrate) }
    var gentle by remember(alarm.id) { mutableStateOf(alarm.gentleWake) }
    var snooze by remember(alarm.id) { mutableIntStateOf(alarm.snoozeMinutes) }
    var showPicker by remember { mutableStateOf(false) }

    EditorDialog(
        title = if (alarm.id == 0L) "New alarm" else "Alarm",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                alarm.copy(
                    hour = hour,
                    minute = minute,
                    label = label.trim(),
                    daysMask = daysMask,
                    vibrate = vibrate,
                    gentleWake = gentle,
                    snoozeMinutes = snooze,
                    enabled = true,
                    skipUntil = 0L
                )
            )
        }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x4D0A1224))
                .clickable { showPicker = true }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                TimeUtils.formatTime(hour, minute, use24h = true),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Repeat")
        Spacer(Modifier.height(8.dp))
        DayPicker(daysMask = daysMask, onChange = { daysMask = it })

        Spacer(Modifier.height(16.dp))
        FieldLabel("Label")
        PlainField(value = label, onChange = { label = it }, placeholder = "Sunrise")

        Spacer(Modifier.height(16.dp))
        ToggleRow("Vibrate", vibrate) { vibrate = it }
        ToggleRow("Fade the volume in", gentle) { gentle = it }

        Spacer(Modifier.height(12.dp))
        FieldLabel("Snooze")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 9, 10, 15, 20).forEach { minutes ->
                ChoiceChip(
                    text = "${minutes}m",
                    selected = snooze == minutes,
                    accent = AuroraTeal,
                    modifier = Modifier.weight(1f)
                ) { snooze = minutes }
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = pickerState.hour
                    minute = pickerState.minute
                    showPicker = false
                }) { Text("Set", color = AuroraTeal) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel", color = StarlightFaint)
                }
            },
            text = { TimePicker(state = pickerState) },
            containerColor = Color(0xF20A1120)
        )
    }
}

@Composable
private fun ReminderEditor(
    reminder: ReminderEntity,
    onDismiss: () -> Unit,
    onSave: (ReminderEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember(reminder.id) { mutableStateOf(reminder.title) }
    var note by remember(reminder.id) { mutableStateOf(reminder.note) }
    var triggerAt by remember(reminder.id) { mutableStateOf(reminder.triggerAt) }
    var repeatRule by remember(reminder.id) { mutableStateOf(reminder.repeat) }

    EditorDialog(
        title = if (reminder.id == 0L) "New reminder" else "Reminder",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                reminder.copy(
                    title = title.trim().ifBlank { "Reminder" },
                    note = note.trim(),
                    triggerAt = triggerAt,
                    repeat = repeatRule,
                    enabled = true
                )
            )
        }
    ) {
        FieldLabel("What")
        PlainField(value = title, onChange = { title = it }, placeholder = "Call the dentist")

        Spacer(Modifier.height(14.dp))
        FieldLabel("Note")
        PlainField(value = note, onChange = { note = it }, placeholder = "Optional detail")

        Spacer(Modifier.height(16.dp))
        DateTimeField(
            millis = triggerAt,
            onChange = { triggerAt = it }
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel("Repeat")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatRule.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { rule ->
                        ChoiceChip(
                            text = rule.label,
                            selected = repeatRule == rule,
                            accent = AuroraTeal,
                            modifier = Modifier.weight(1f)
                        ) { repeatRule = rule }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ shared

@Composable
internal fun EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF2080E1C),
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            // Editors can be taller than a short screen; the dialog body scrolls.
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save", color = AuroraTeal) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = PulsarRose) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = StarlightFaint) }
            }
        }
    )
}

@Composable
internal fun DayPicker(daysMask: Int, onChange: (Int) -> Unit) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val bit = 1 shl index
            val selected = daysMask and bit != 0
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) AuroraTeal.copy(alpha = 0.18f) else Color(0x330A1224))
                    .border(
                        1.dp,
                        if (selected) AuroraTeal.copy(alpha = 0.55f) else SpaceOutlineSoft,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onChange(daysMask xor bit) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) AuroraTeal else StarlightFaint
                )
            }
        }
    }
}

@Composable
internal fun ChoiceChip(
    text: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color(0x330A1224))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.55f) else SpaceOutlineSoft,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else StarlightFaint
        )
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = StarlightDim)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AuroraTeal,
                checkedTrackColor = AuroraTeal.copy(alpha = 0.28f),
                uncheckedThumbColor = StarlightFaint,
                uncheckedTrackColor = Color(0x330A1224)
            )
        )
    }
}
