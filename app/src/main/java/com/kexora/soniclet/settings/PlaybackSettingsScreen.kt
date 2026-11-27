package com.kexora.soniclet.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.SleepTimerOption
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.player.SleepTimerDialog

/**
 * Clean, balanced, modern, and fluid Playback Settings Screen.
 * Uses grouped rounded surfaces with compact typography and balanced spacing.
 */
@Composable
fun PlaybackSettingsSubPage(
    playTogetherEnabled: Boolean,
    onSetPlayTogetherEnabled: (Boolean) -> Unit,
    playTogetherFadeEnabled: Boolean = true,
    onSetPlayTogetherFadeEnabled: (Boolean) -> Unit = {},
    sleepTimerOption: SleepTimerOption,
    customSleepTimerMinutes: Int,
    sleepTimerRemainingMs: Long,
    isCrossfadeEnabled: Boolean,
    isAutoResumeOnPlugEnabled: Boolean,
    isAutoPlayOnBluetoothEnabled: Boolean,
    finishTrackBeforeSleep: Boolean,
    singleClickAction: String,
    doubleClickAction: String,
    tripleClickAction: String,
    isHearingSafetyEnabled: Boolean,
    isGaplessPlaybackEnabled: Boolean,
    isResumeLastPositionEnabled: Boolean,
    crossfadeDurationSeconds: Int,
    onSetSleepTimer: (SleepTimerOption) -> Unit,
    onSetCustomSleepTimer: (Int) -> Unit,
    onToggleFinishTrackBeforeSleep: (Boolean) -> Unit,
    onSetCrossfadeEnabled: (Boolean) -> Unit,
    onSetAutoResumeOnPlugEnabled: (Boolean) -> Unit,
    onSetAutoPlayOnBluetoothEnabled: (Boolean) -> Unit,
    onSetSingleClickAction: (String) -> Unit,
    onSetDoubleClickAction: (String) -> Unit,
    onSetTripleClickAction: (String) -> Unit,
    onSetHearingSafetyEnabled: (Boolean) -> Unit,
    onSetGaplessPlaybackEnabled: (Boolean) -> Unit,
    onSetResumeLastPositionEnabled: (Boolean) -> Unit,
    onSetCrossfadeDuration: (Int) -> Unit
) {
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Audio Engine & Transitions
        SettingsSectionGroup(title = "AUDIO ENGINE & TRANSITIONS") {
            SettingsItemRow(
                icon = Icons.Rounded.AllInclusive,
                title = "Gapless Playback",
                subtitle = "Zero silence between consecutive tracks for live & DJ albums",
                trailing = {
                    Switch(
                        checked = isGaplessPlaybackEnabled,
                        onCheckedChange = onSetGaplessPlaybackEnabled,
                        colors = modernSwitchColors()
                    )
                },
                onClick = { onSetGaplessPlaybackEnabled(!isGaplessPlaybackEnabled) }
            )

            CompactDivider()

            SettingsItemRow(
                icon = Icons.Rounded.History,
                title = "Resume Last Playback Position",
                subtitle = "Remember position when switching songs. When off, songs always start from the beginning",
                trailing = {
                    Switch(
                        checked = isResumeLastPositionEnabled,
                        onCheckedChange = onSetResumeLastPositionEnabled,
                        colors = modernSwitchColors()
                    )
                },
                onClick = { onSetResumeLastPositionEnabled(!isResumeLastPositionEnabled) }
            )

            CompactDivider()

            SettingsItemRow(
                icon = Icons.Rounded.Tune,
                title = "DJ Track Crossfade",
                subtitle = "Smoothly blend track endings into upcoming songs",
                trailing = {
                    Switch(
                        checked = isCrossfadeEnabled,
                        onCheckedChange = onSetCrossfadeEnabled,
                        colors = modernSwitchColors()
                    )
                },
                onClick = { onSetCrossfadeEnabled(!isCrossfadeEnabled) }
            )

            AnimatedVisibility(
                visible = isCrossfadeEnabled,
                enter = expandVertically(tween(180)) + fadeIn(),
                exit = shrinkVertically(tween(150)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp, end = 4.dp, bottom = 8.dp, top = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overlap Duration",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${crossfadeDurationSeconds}s",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 2, 3, 5, 8, 12).forEach { sec ->
                            val isSelected = crossfadeDurationSeconds == sec
                            Surface(
                                onClick = { onSetCrossfadeDuration(sec) },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CompactDivider()

            SettingsItemRow(
                icon = Icons.Rounded.SpatialAudioOff,
                title = "Play Together with Other Apps",
                subtitle = "Keep music playing alongside games, videos, or navigation",
                trailing = {
                    Switch(
                        checked = playTogetherEnabled,
                        onCheckedChange = onSetPlayTogetherEnabled,
                        colors = modernSwitchColors()
                    )
                },
                onClick = { onSetPlayTogetherEnabled(!playTogetherEnabled) }
            )

            AnimatedVisibility(
                visible = playTogetherEnabled,
                enter = expandVertically(tween(180)) + fadeIn(),
                exit = shrinkVertically(tween(150)) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CompactDivider()
                    SettingsItemRow(
                        icon = Icons.Rounded.VolumeDown,
                        title = "Smooth Audio Ducking",
                        subtitle = "Gently lower music volume when calls or notifications arrive",
                        trailing = {
                            Switch(
                                checked = playTogetherFadeEnabled,
                                onCheckedChange = onSetPlayTogetherFadeEnabled,
                                colors = modernSwitchColors()
                            )
                        },
                        onClick = { onSetPlayTogetherFadeEnabled(!playTogetherFadeEnabled) }
                    )
                }
            }
        }

        // Section 2: External Files & Floating Popup
        SettingsSectionGroup(title = "EXTERNAL FILES & VOICE NOTES") {
            SettingsItemRow(
                icon = Icons.Rounded.PictureInPictureAlt,
                title = "Quick Floating Audio Popup",
                subtitle = "Opens songs & voice recordings in a mini popup directly inside File Manager or WhatsApp without opening full app",
                trailing = {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "Enabled",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            )
        }

        // Section 3: Automations & Connectivity
        SettingsSectionGroup(title = "AUTOMATIONS & CONNECTIVITY") {
            SettingsItemRow(
                icon = Icons.Rounded.Headphones,
                title = "Auto-Resume on Earphones",
                subtitle = "Resumes playback when 3.5mm or Type-C headphones plug in",
                trailing = {
                    Switch(
                        checked = isAutoResumeOnPlugEnabled,
                        onCheckedChange = onSetAutoResumeOnPlugEnabled,
                        colors = modernSwitchColors()
                    )
                },
                onClick = { onSetAutoResumeOnPlugEnabled(!isAutoResumeOnPlugEnabled) }
            )

            CompactDivider()

            SettingsItemRow(
                icon = Icons.Rounded.BluetoothAudio,
                title = "Auto-Play on Bluetooth",
                subtitle = "Starts playback automatically when paired with car audio or speakers",
                trailing = {
                    Switch(
                        checked = isAutoPlayOnBluetoothEnabled,
                        onCheckedChange = onSetAutoPlayOnBluetoothEnabled,
                        colors = modernSwitchColors()
                    )
                },
                onClick = { onSetAutoPlayOnBluetoothEnabled(!isAutoPlayOnBluetoothEnabled) }
            )
        }

        // Section 3: Headphone Button Controls
        SettingsSectionGroup(title = "HEADPHONE BUTTON CONTROLS") {
            val actions = listOf(
                "Play/Pause",
                "Next",
                "Previous",
                "Volume Up",
                "Volume Down",
                "Fast Forward",
                "Rewind",
                "Favorite",
                "None"
            )

            SettingsDropdownRow(
                icon = Icons.Rounded.TouchApp,
                title = "Single Click Action",
                currentValue = singleClickAction,
                options = actions,
                onSelect = onSetSingleClickAction
            )

            CompactDivider()

            SettingsDropdownRow(
                icon = Icons.Rounded.DoubleArrow,
                title = "Double Click Action",
                currentValue = doubleClickAction,
                options = actions,
                onSelect = onSetDoubleClickAction
            )

            CompactDivider()

            SettingsDropdownRow(
                icon = Icons.Rounded.FastForward,
                title = "Triple Click Action",
                currentValue = tripleClickAction,
                options = actions,
                onSelect = onSetTripleClickAction
            )
        }

        // Section 4: Timer & Hearing Safety
        SettingsSectionGroup(title = "TIMER & HEARING SAFETY") {
            val isTimerActive = sleepTimerOption != SleepTimerOption.OFF

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SettingsItemRow(
                    icon = Icons.Rounded.Bedtime,
                    title = "Sleep Timer",
                    subtitle = if (sleepTimerRemainingMs > 0L) {
                        val totalSec = sleepTimerRemainingMs / 1000
                        val min = totalSec / 60
                        val sec = totalSec % 60
                        String.format("%02d:%02d remaining before fadeout", min, sec)
                    } else if (isTimerActive) {
                        "Active: ${sleepTimerOption.label}"
                    } else {
                        "Auto-pause playback after set duration"
                    },
                    trailing = {
                        Surface(
                            onClick = { showSleepTimerDialog = true },
                            shape = CircleShape,
                            color = if (isTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (isTimerActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ) {
                            Text(
                                text = if (isTimerActive) "Active" else "Set",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    },
                    onClick = { showSleepTimerDialog = true }
                )

                // Compact Quick Preset Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp, top = 4.dp, bottom = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        SleepTimerOption.M15 to "15m",
                        SleepTimerOption.M30 to "30m",
                        SleepTimerOption.M45 to "45m",
                        SleepTimerOption.M60 to "60m",
                        SleepTimerOption.END_OF_SONG to "End of Song"
                    )

                    presets.forEach { (option, label) ->
                        val isSelected = sleepTimerOption == option
                        Surface(
                            onClick = {
                                if (isSelected) {
                                    onSetSleepTimer(SleepTimerOption.OFF)
                                } else {
                                    onSetSleepTimer(option)
                                }
                            },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            CompactDivider()

            SettingsItemRow(
                icon = Icons.Rounded.Hearing,
                title = "WHO Volume Warning & Limiter",
                subtitle = "Warns on prolonged high-volume playback to protect hearing",
                trailing = {
                    Switch(
                        checked = isHearingSafetyEnabled,
                        onCheckedChange = onSetHearingSafetyEnabled,
                        colors = modernSwitchColors()
                    )
                },
                onClick = { onSetHearingSafetyEnabled(!isHearingSafetyEnabled) }
            )
        }

        MadeInIndiaFooter()
        Spacer(modifier = Modifier.height(com.kexora.soniclet.LocalPlayerBottomPadding.current + 8.dp))
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentOption = sleepTimerOption,
            remainingMs = sleepTimerRemainingMs,
            finishTrackBeforeSleep = finishTrackBeforeSleep,
            onSelectOption = { option ->
                if (option == SleepTimerOption.CUSTOM) {
                    onSetCustomSleepTimer(20)
                } else {
                    onSetSleepTimer(option)
                }
                showSleepTimerDialog = false
            },
            onToggleFinishTrackBeforeSleep = onToggleFinishTrackBeforeSleep,
            onDismiss = { showSleepTimerDialog = false }
        )
    }
}

/**
 * Reusable clean grouped container for related settings
 */
@Composable
private fun SettingsSectionGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                content = content
            )
        }
    }
}

/**
 * Single clean setting row with leading icon, title, subtitle, and trailing control
 */
@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.5.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        trailing()
    }
}

/**
 * Dropdown setting item row with compact selection badge
 */
@Composable
private fun SettingsDropdownRow(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Box {
            Surface(
                onClick = { expanded = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentValue,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { action ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = action,
                                fontWeight = if (action == currentValue) FontWeight.Bold else FontWeight.Normal,
                                color = if (action == currentValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSelect(action)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Compact subtle divider between items inside a group
 */
@Composable
private fun CompactDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 44.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
    )
}

/**
 * Modern switch colors
 */
@Composable
private fun modernSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.primary,
    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
)
