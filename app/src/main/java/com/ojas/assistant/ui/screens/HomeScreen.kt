package com.ojas.assistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojas.assistant.assistant.OjasAction
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OrbitIconButton
import com.ojas.assistant.ui.components.OrbitRing
import com.ojas.assistant.ui.components.OjasScreen
import com.ojas.assistant.ui.components.SectionLabel
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.navigation.Routes
import com.ojas.assistant.ui.theme.AuroraTeal
import com.ojas.assistant.ui.theme.EmberOrange
import com.ojas.assistant.ui.theme.NebulaBlue
import com.ojas.assistant.ui.theme.PulsarRose
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import com.ojas.assistant.ui.theme.VioletDrift
import com.ojas.assistant.ui.viewmodel.HomeViewModel
import com.ojas.assistant.ui.viewmodel.UpcomingKind

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onNavigate: (String) -> Unit,
    onOpenWorkout: (Long) -> Unit
) {
    val container = LocalOjas.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val thinking by viewModel.thinking.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingAction.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshScreenTime() }

    // The brain answers with an intent; the screen is what knows how to act on it.
    LaunchedEffect(pendingAction) {
        when (val action = pendingAction) {
            null, OjasAction.None -> Unit
            is OjasAction.Navigate -> {
                Routes.fromNotification(action.route)?.let(onNavigate)
                viewModel.consumeAction()
            }
            is OjasAction.StartWorkout -> {
                onOpenWorkout(action.workoutId)
                viewModel.consumeAction()
            }
            is OjasAction.StartMeditation -> {
                onNavigate(Routes.MEDITATION)
                viewModel.consumeAction()
            }
            is OjasAction.StartFocus -> {
                onNavigate(Routes.SCREEN_TIME)
                viewModel.consumeAction()
            }
        }
    }

    OjasScreen(
        title = state.greeting + (state.settings.userName.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""),
        subtitle = TimeUtils.formatFullDay(TimeUtils.todayEpochDay()),
        contentPadding = contentPadding,
        actions = {
            OrbitIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                onClick = { onNavigate(Routes.SETTINGS) },
                tint = StarlightDim
            )
        }
    ) {
        // A window onto the galaxy running behind everything. Nothing is drawn here on
        // purpose: the empty space is the point.
        item(key = "viewport") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(196.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = if (state.settings.galaxyInteractive) "drag to turn  ·  pinch to zoom" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarlightFaint.copy(alpha = 0.7f)
                )
            }
        }

        item(key = "assistant") {
            AssistantConsole(
                lines = chat.takeLast(4),
                thinking = thinking,
                onAsk = viewModel::ask,
                onClear = viewModel::clearChat
            )
        }

        item(key = "rings") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RingTile(
                    modifier = Modifier.weight(1f),
                    progress = state.waterProgress,
                    accent = NebulaBlue,
                    value = "${state.waterMl}",
                    unit = "ml",
                    label = "Water",
                    onClick = { onNavigate(Routes.WATER) }
                )
                RingTile(
                    modifier = Modifier.weight(1f),
                    progress = (state.meditationSeconds / 60f) /
                        state.settings.meditationDefaultMinutes.coerceAtLeast(1),
                    accent = VioletDrift,
                    value = "${state.meditationSeconds / 60}",
                    unit = "min",
                    label = "Calm",
                    onClick = { onNavigate(Routes.MEDITATION) }
                )
                RingTile(
                    modifier = Modifier.weight(1f),
                    progress = if (state.workoutsToday > 0) 1f else 0f,
                    accent = EmberOrange,
                    value = if (state.workoutStreak > 0) "${state.workoutStreak}" else "${state.workoutsToday}",
                    unit = if (state.workoutStreak > 0) "day" else "done",
                    label = "Move",
                    onClick = { onNavigate(Routes.WORKOUT) }
                )
            }
        }

        item(key = "quick-water") {
            QuickHydration(
                cupMl = state.settings.waterCupMl,
                onLog = viewModel::logWater
            )
        }

        item(key = "upcoming-label") {
            SectionLabel(
                text = "On the horizon",
                trailing = if (state.upcoming.isEmpty()) null else "${state.upcoming.size}"
            )
        }

        if (state.upcoming.isEmpty()) {
            item(key = "upcoming-empty") {
                OjasCard {
                    Text(
                        "Nothing scheduled. Ask me to set an alarm or a reminder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StarlightDim
                    )
                }
            }
        } else {
            items(state.upcoming, key = { it.key }) { item ->
                val accent = when (item.kind) {
                    UpcomingKind.ALARM -> PulsarRose
                    UpcomingKind.REMINDER -> AuroraTeal
                    UpcomingKind.EVENT -> VioletDrift
                }
                val icon = when (item.kind) {
                    UpcomingKind.ALARM -> Icons.Rounded.Alarm
                    UpcomingKind.REMINDER -> Icons.Outlined.SelfImprovement
                    UpcomingKind.EVENT -> Icons.Outlined.CalendarMonth
                }
                OjasCard(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    onClick = {
                        onNavigate(
                            when (item.kind) {
                                UpcomingKind.ALARM -> Routes.schedule(Routes.TAB_ALARMS)
                                UpcomingKind.REMINDER -> Routes.schedule(Routes.TAB_REMINDERS)
                                UpcomingKind.EVENT -> Routes.schedule(Routes.TAB_CALENDAR)
                            }
                        )
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                item.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = StarlightFaint
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                TimeUtils.formatTime(item.at, state.settings.use24h),
                                style = MaterialTheme.typography.titleMedium,
                                color = accent
                            )
                            Text(
                                TimeUtils.relative(item.at, use24h = state.settings.use24h),
                                style = MaterialTheme.typography.labelSmall,
                                color = StarlightFaint
                            )
                        }
                    }
                }
            }
        }

        item(key = "shortcuts-label") { SectionLabel("Elsewhere") }

        item(key = "shortcuts") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "Screen time",
                    detail = if (state.screenTimeAvailable) {
                        TimeUtils.humanDuration(state.screenTimeMinutes * 60)
                    } else {
                        "Set up"
                    },
                    accent = AuroraTeal,
                    onClick = { onNavigate(Routes.SCREEN_TIME) }
                )
                ShortcutTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CalendarMonth,
                    title = "Calendar",
                    detail = "Plan",
                    accent = VioletDrift,
                    onClick = { onNavigate(Routes.schedule(Routes.TAB_CALENDAR)) }
                )
                ShortcutTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Whatshot,
                    title = "Routines",
                    detail = "${state.workoutsToday} today",
                    accent = EmberOrange,
                    onClick = { onNavigate(Routes.WORKOUT) }
                )
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun AssistantConsole(
    lines: List<com.ojas.assistant.ui.viewmodel.ChatLine>,
    thinking: Boolean,
    onAsk: (String) -> Unit,
    onClear: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    OjasCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (thinking) EmberOrange else NebulaBlue)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = if (thinking) "OJAS · thinking" else "OJAS · listening",
                style = MaterialTheme.typography.labelMedium,
                color = StarlightFaint,
                modifier = Modifier.weight(1f)
            )
            if (lines.isNotEmpty()) {
                Text(
                    "clear",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarlightFaint,
                    modifier = Modifier.clickable(onClick = onClear)
                )
            }
        }

        AnimatedVisibility(visible = lines.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.padding(top = 10.dp)) {
                lines.forEach { line ->
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (line.fromUser) StarlightDim else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (line.fromUser) FontWeight.Normal else FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "log 250 ml · alarm at 6:30 · remind me…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StarlightFaint
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x660A1224),
                    unfocusedContainerColor = Color(0x4D0A1224),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NebulaBlue
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        onAsk(draft)
                        draft = ""
                        keyboard?.hide()
                    }
                )
            )
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(NebulaBlue.copy(alpha = 0.18f))
                    .border(1.dp, NebulaBlue.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        onAsk(draft)
                        draft = ""
                        keyboard?.hide()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Ask Ojas",
                    tint = NebulaBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RingTile(
    progress: Float,
    accent: Color,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OjasCard(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 15.dp, horizontal = 8.dp),
        onClick = onClick
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OrbitRing(
                progress = progress,
                accent = accent,
                strokeWidth = 6.dp,
                modifier = Modifier.size(74.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = StarlightFaint)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = StarlightDim)
        }
    }
}

@Composable
private fun QuickHydration(cupMl: Int, onLog: (Int) -> Unit) {
    val options = remember(cupMl) {
        listOf(cupMl, cupMl * 2, 100, 500).distinct().sorted()
    }
    OjasCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = NebulaBlue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Log a drink",
                style = MaterialTheme.typography.labelMedium,
                color = StarlightDim,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { amount ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NebulaBlue.copy(alpha = 0.10f))
                        .border(1.dp, SpaceOutlineSoft, RoundedCornerShape(14.dp))
                        .clickable { onLog(amount) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$amount",
                        style = MaterialTheme.typography.labelLarge,
                        color = NebulaBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OjasCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(detail, style = MaterialTheme.typography.labelSmall, color = StarlightFaint)
    }
}
