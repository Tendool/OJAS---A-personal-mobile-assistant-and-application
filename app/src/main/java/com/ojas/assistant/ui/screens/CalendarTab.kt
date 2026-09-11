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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.entity.CalendarEventEntity
import com.ojas.assistant.data.local.entity.RepeatRule
import com.ojas.assistant.ui.components.EmptyState
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OjasScreen
import com.ojas.assistant.ui.components.OrbitIconButton
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.theme.AuroraTeal
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import com.ojas.assistant.ui.theme.VioletDrift
import com.ojas.assistant.ui.theme.accentFor
import com.ojas.assistant.ui.theme.accentKeys
import com.ojas.assistant.ui.viewmodel.CalendarViewModel
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun CalendarTab(
    contentPadding: PaddingValues,
    tabIndex: Int,
    onTabChange: (Int) -> Unit
) {
    val container = LocalOjas.current
    val viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<CalendarEventEntity?>(null) }

    OjasScreen(
        title = "Plan",
        subtitle = TimeUtils.formatFullDay(state.selectedDay),
        contentPadding = contentPadding,
        actions = {
            OrbitIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "New event",
                onClick = {
                    val start = TimeUtils.startOfDayMillis(state.selectedDay) + 9 * 3_600_000L
                    editing = CalendarEventEntity(
                        title = "",
                        startAt = start,
                        endAt = start + 3_600_000L,
                        epochDay = state.selectedDay
                    )
                },
                tint = VioletDrift
            )
        }
    ) {
        item(key = "tabs") { ScheduleTabs(selected = tabIndex, onSelect = onTabChange) }

        item(key = "grid") {
            OjasCard(contentPadding = PaddingValues(14.dp)) {
                MonthHeader(
                    month = state.visibleMonth,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    onToday = viewModel::today
                )
                Spacer(Modifier.height(12.dp))
                MonthGrid(
                    month = state.visibleMonth,
                    selectedDay = state.selectedDay,
                    busyDays = state.busyDays,
                    onSelect = viewModel::select
                )
            }
        }

        if (state.dayEvents.isEmpty()) {
            item(key = "day-empty") {
                OjasCard {
                    EmptyState(
                        title = "Clear skies",
                        body = "Nothing scheduled for this day."
                    )
                }
            }
        } else {
            items(state.dayEvents, key = { it.id }) { event ->
                val accent = accentFor(event.colorKey)
                OjasCard(
                    contentPadding = PaddingValues(16.dp),
                    onClick = { editing = event }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                event.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (event.note.isNotBlank()) {
                                Text(
                                    event.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StarlightFaint
                                )
                            }
                        }
                        Text(
                            if (event.allDay) {
                                "All day"
                            } else {
                                TimeUtils.formatTime(event.startAt, state.settings.use24h)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent
                        )
                    }
                }
            }
        }
    }

    editing?.let { event ->
        EventEditor(
            event = event,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = if (event.id == 0L) null else {
                {
                    viewModel.delete(event.id)
                    editing = null
                }
            }
        )
    }
}

@Composable
private fun MonthHeader(
    month: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.ChevronLeft,
            contentDescription = "Previous month",
            tint = StarlightDim,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onPrevious)
        )
        Text(
            TimeUtils.formatMonth(month),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToday)
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = "Next month",
            tint = StarlightDim,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onNext)
        )
    }
}

/**
 * Six fixed rows of seven. A constant row count keeps the card height stable as the user
 * pages between months, which matters more here than trimming an empty trailing week.
 */
@Composable
private fun MonthGrid(
    month: LocalDate,
    selectedDay: Long,
    busyDays: Set<Long>,
    onSelect: (Long) -> Unit
) {
    val today = TimeUtils.todayEpochDay()
    val firstOfMonth = month.withDayOfMonth(1)
    // Monday-first grid: DayOfWeek.value is 1..7 starting Monday.
    val leading = firstOfMonth.dayOfWeek.value - 1
    val gridStart = firstOfMonth.toEpochDay() - leading

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = StarlightFaint,
                    textAlign = TextAlign.Center
                )
            }
        }
        repeat(6) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { weekday ->
                    val epochDay = gridStart + week * 7 + weekday
                    val date = LocalDate.ofEpochDay(epochDay)
                    val inMonth = date.month == month.month
                    val selected = epochDay == selectedDay
                    val isToday = epochDay == today

                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                when {
                                    selected -> VioletDrift.copy(alpha = 0.20f)
                                    isToday -> Color(0x330A1224)
                                    else -> Color.Transparent
                                }
                            )
                            .then(
                                if (isToday && !selected) {
                                    Modifier.border(1.dp, SpaceOutlineSoft, RoundedCornerShape(11.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelect(epochDay) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${date.dayOfMonth}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    selected -> VioletDrift
                                    !inMonth -> StarlightFaint.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (busyDays.contains(epochDay)) {
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) VioletDrift else AuroraTeal)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventEditor(
    event: CalendarEventEntity,
    onDismiss: () -> Unit,
    onSave: (CalendarEventEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember(event.id) { mutableStateOf(event.title) }
    var note by remember(event.id) { mutableStateOf(event.note) }
    var startAt by remember(event.id) { mutableStateOf(event.startAt) }
    var durationMinutes by remember(event.id) {
        mutableStateOf(((event.endAt - event.startAt) / 60_000L).toInt().coerceAtLeast(15))
    }
    var allDay by remember(event.id) { mutableStateOf(event.allDay) }
    var colorKey by remember(event.id) { mutableStateOf(event.colorKey) }
    var alertMinutes by remember(event.id) { mutableStateOf(event.reminderMinutesBefore) }
    var repeatRule by remember(event.id) { mutableStateOf(event.repeat) }

    EditorDialog(
        title = if (event.id == 0L) "New event" else "Event",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            val normalisedStart = if (allDay) {
                TimeUtils.toMillis(
                    LocalDateTime.of(TimeUtils.localDateTime(startAt).toLocalDate(), java.time.LocalTime.MIDNIGHT)
                )
            } else {
                startAt
            }
            onSave(
                event.copy(
                    title = title.trim().ifBlank { "Untitled" },
                    note = note.trim(),
                    startAt = normalisedStart,
                    endAt = normalisedStart + if (allDay) 86_400_000L else durationMinutes * 60_000L,
                    allDay = allDay,
                    colorKey = colorKey,
                    reminderMinutesBefore = alertMinutes,
                    repeat = repeatRule
                )
            )
        }
    ) {
        FieldLabel("Title")
        PlainField(value = title, onChange = { title = it }, placeholder = "Dentist")

        Spacer(Modifier.height(14.dp))
        FieldLabel("Note")
        PlainField(value = note, onChange = { note = it }, placeholder = "Optional detail")

        Spacer(Modifier.height(16.dp))
        DateTimeField(millis = startAt, onChange = { startAt = it }, label = "Starts")

        Spacer(Modifier.height(12.dp))
        ToggleRow("All day", allDay) { allDay = it }

        if (!allDay) {
            Spacer(Modifier.height(8.dp))
            FieldLabel("Lasts")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 90, 120).forEach { minutes ->
                    ChoiceChip(
                        text = if (minutes >= 60) "${minutes / 60}h" else "${minutes}m",
                        selected = durationMinutes == minutes,
                        accent = VioletDrift,
                        modifier = Modifier.weight(1f)
                    ) { durationMinutes = minutes }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Alert")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-1 to "None", 0 to "On time", 10 to "10m", 30 to "30m", 60 to "1h").forEach { (value, label) ->
                ChoiceChip(
                    text = label,
                    selected = alertMinutes == value,
                    accent = VioletDrift,
                    modifier = Modifier.weight(1f)
                ) { alertMinutes = value }
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Repeat")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatRule.entries.filter { it != RepeatRule.HOURLY }.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { rule ->
                        ChoiceChip(
                            text = rule.label,
                            selected = repeatRule == rule,
                            accent = VioletDrift,
                            modifier = Modifier.weight(1f)
                        ) { repeatRule = rule }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Colour")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            accentKeys.forEach { key ->
                val colour = accentFor(key)
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colour.copy(alpha = if (key == colorKey) 0.95f else 0.28f))
                        .border(
                            if (key == colorKey) 2.dp else 1.dp,
                            if (key == colorKey) Color.White else SpaceOutlineSoft,
                            CircleShape
                        )
                        .clickable { colorKey = key }
                )
            }
        }
    }
}
