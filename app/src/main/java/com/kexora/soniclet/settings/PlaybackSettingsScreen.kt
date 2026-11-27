package com.kexora.soniclet.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.SleepTimerOption
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.player.SleepTimerDialog

/**
 * Box-free, edge-to-edge Playback Engine & Automations Settings Screen.
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
    ) {
        // Section 1: Audio Engine & Transitions
        EdgeSectionHeader(title = "AUDIO ENGINE & TRANSITIONS")

        PlaybackItemRow(
            icon = Icons.Rounded.AllInclusive,
            title = "Gapless Playback",
            subtitle = "Zero silence between consecutive tracks for live & DJ albums",
            trailing = {
                Switch(
                    checked = isGaplessPlaybackEnabled,
                    onCheckedChange = onSetGaplessPlaybackEnabled,
                    colors = sonicletSwitchColors()
                )
            },
            onClick = { onSetGaplessPlaybackEnabled(!isGaplessPlaybackEnabled) }
        )

        EdgeHairlineDivider()

        PlaybackItemRow(
            icon = Icons.Rounded.History,
            title = "Resume Last Playback Position",
            subtitle = "Remember timestamp when switching songs; off always starts at 0:00",
            trailing = {
                Switch(
                    checked = isResumeLastPositionEnabled,
                    onCheckedChange = onSetResumeLastPositionEnabled,
                    colors = sonicletSwitchColors()
                )
            },
            onClick = { onSetResumeLastPositionEnabled(!isResumeLastPositionEnabled) }
        )

        EdgeHairlineDivider()

        PlaybackItemRow(
            icon = Icons.Rounded.Tune,
            title = "DJ Track Crossfade",
            subtitle = "Smoothly blend track endings into upcoming songs",
            trailing = {
                Switch(
                    checked = isCrossfadeEnabled,
                    onCheckedChange = onSetCrossfadeEnabled,
                    colors = sonicletSwitchColors()
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
                    .padding(start = 54.dp, end = 16.dp, bottom = 12.dp, top = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Overlap Duration",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${crossfadeDurationSeconds}s",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 2, 3, 5, 8, 12).forEach { sec ->
                        val isSelected = crossfadeDurationSeconds == sec
                        Surface(
                            onClick = { onSetCrossfadeDuration(sec) },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${sec}s",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        EdgeHairlineDivider()

        PlaybackItemRow(
            icon = Icons.Rounded.SpatialAudioOff,
            title = "Play Together with Other Apps",
            subtitle = "Keep music playing alongside games, videos, or navigation",
            trailing = {
                Switch(
                    checked = playTogetherEnabled,
                    onCheckedChange = onSetPlayTogetherEnabled,
                    colors = sonicletSwitchColors()
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
                EdgeHairlineDivider()
                PlaybackItemRow(
                    icon = Icons.Rounded.VolumeDown,
                    title = "Smooth Audio Ducking",
                    subtitle = "Gently lower music volume when calls or notifications arrive",
                    trailing = {
                        Switch(
                            checked = playTogetherFadeEnabled,
                            onCheckedChange = onSetPlayTogetherFadeEnabled,
                            colors = sonicletSwitchColors()
                        )
                    },
                    onClick = { onSetPlayTogetherFadeEnabled(!playTogetherFadeEnabled) }
                )
            }
        }

        // Section 2: External Files & Voice Notes
        EdgeSectionHeader(title = "EXTERNAL FILES & VOICE NOTES")

        PlaybackItemRow(
            icon = Icons.Rounded.PictureInPictureAlt,
            title = "Quick Floating Audio Popup",
            subtitle = "Opens songs & voice notes in a mini popup inside File Manager or WhatsApp",
            trailing = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "Enabled",
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

        // Section 3: Automations & Connectivity
        EdgeSectionHeader(title = "AUTOMATIONS & CONNECTIVITY")

        PlaybackItemRow(
            icon = Icons.Rounded.Headphones,
            title = "Auto-Resume on Earphones",
            subtitle = "Resumes playback when wired 3.5mm or USB-C headphones plug in",
            trailing = {
                Switch(
                    checked = isAutoResumeOnPlugEnabled,
                    onCheckedChange = onSetAutoResumeOnPlugEnabled,
                    colors = sonicletSwitchColors()
                )
            },
            onClick = { onSetAutoResumeOnPlugEnabled(!isAutoResumeOnPlugEnabled) }
        )

        EdgeHairlineDivider()

        PlaybackItemRow(
            icon = Icons.Rounded.BluetoothAudio,
            title = "Auto-Play on Bluetooth",
            subtitle = "Starts playback automatically when paired with car audio or speakers",
            trailing = {
                Switch(
                    checked = isAutoPlayOnBluetoothEnabled,
                    onCheckedChange = onSetAutoPlayOnBluetoothEnabled,
                    colors = sonicletSwitchColors()
                )
            },
            onClick = { onSetAutoPlayOnBluetoothEnabled(!isAutoPlayOnBluetoothEnabled) }
        )

        // Section 4: Headphone Button Controls
        EdgeSectionHeader(title = "HEADPHONE BUTTON CONTROLS")

        val actions = remember {
            listOf(
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
        }

        PlaybackDropdownRow(
            icon = Icons.Rounded.TouchApp,
            title = "Single Click Action",
            currentValue = singleClickAction,
            options = actions,
            onSelect = onSetSingleClickAction
        )

        EdgeHairlineDivider()

        PlaybackDropdownRow(
            icon = Icons.Rounded.DoubleArrow,
            title = "Double Click Action",
            currentValue = doubleClickAction,
            options = actions,
            onSelect = onSetDoubleClickAction
        )

        EdgeHairlineDivider()

        PlaybackDropdownRow(
            icon = Icons.Rounded.FastForward,
            title = "Triple Click Action",
            currentValue = tripleClickAction,
            options = actions,
            onSelect = onSetTripleClickAction
        )

        // Section 5: Timer & Hearing Safety
        EdgeSectionHeader(title = "TIMER & HEARING SAFETY")

        val isTimerActive = sleepTimerOption != SleepTimerOption.OFF

        PlaybackItemRow(
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
                "Auto-pause playback smoothly after a set duration"
            },
            trailing = {
                Surface(
                    onClick = { showSleepTimerDialog = true },
                    shape = CircleShape,
                    color = if (isTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isTimerActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ) {
                    Text(
                        text = if (isTimerActive) "Active" else "Set",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            },
            onClick = { showSleepTimerDialog = true }
        )

        val presets = remember {
            listOf(
                SleepTimerOption.M15 to "15m",
                SleepTimerOption.M30 to "30m",
                SleepTimerOption.M45 to "45m",
                SleepTimerOption.M60 to "60m",
                SleepTimerOption.END_OF_SONG to "End of Song"
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            contentPadding = PaddingValues(start = 54.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets) { (option, label) ->
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
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        EdgeHairlineDivider()

        PlaybackItemRow(
            icon = Icons.Rounded.Hearing,
            title = "WHO Volume Warning & Limiter",
            subtitle = "Warns on prolonged high-volume playback to protect hearing",
            trailing = {
                Switch(
                    checked = isHearingSafetyEnabled,
                    onCheckedChange = onSetHearingSafetyEnabled,
                    colors = sonicletSwitchColors()
                )
            },
            onClick = { onSetHearingSafetyEnabled(!isHearingSafetyEnabled) }
        )

        MadeInIndiaFooter()
        EdgeSettingsBottomSpacer()
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

@Composable
private fun PlaybackItemRow(
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
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        trailing()
    }
}

@Composable
private fun PlaybackDropdownRow(
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
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Box {
            Surface(
                onClick = { expanded = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentValue,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
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
