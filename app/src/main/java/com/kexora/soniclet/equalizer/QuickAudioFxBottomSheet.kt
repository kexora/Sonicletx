package com.kexora.soniclet.equalizer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel

private val QUICK_REVERB_PRESETS = listOf(
    0.toShort() to "None",
    1.toShort() to "Small Room",
    2.toShort() to "Medium Room",
    3.toShort() to "Large Room",
    4.toShort() to "Medium Hall",
    5.toShort() to "Large Hall",
    6.toShort() to "Plate"
)

private val BASS_VIBRATION_MODES = listOf("Dynamic", "Sub-Bass", "Punchy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAudioFxBottomSheet(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onOpenFullEqualizer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Calculate count of currently active sound processing effects
    val activeEffectsCount = remember(
        uiState.isEqEnabled,
        uiState.isBassBoostEnabled,
        uiState.bassBoostLevel,
        uiState.isBassVibrationEnabled,
        uiState.isVirtualizerEnabled,
        uiState.virtualizerLevel,
        uiState.is8dAudioEnabled,
        uiState.isDolbyEnabled,
        uiState.isReverbEnabled,
        uiState.isVolumeBoostEnabled,
        uiState.volumeBoostLevel
    ) {
        listOf(
            uiState.isEqEnabled,
            uiState.isBassBoostEnabled && uiState.bassBoostLevel > 0,
            uiState.isBassVibrationEnabled,
            uiState.isVirtualizerEnabled && uiState.virtualizerLevel > 0,
            uiState.is8dAudioEnabled,
            uiState.isDolbyEnabled,
            uiState.isReverbEnabled && uiState.reverbPreset > 0,
            uiState.isVolumeBoostEnabled && uiState.volumeBoostLevel > 100
        ).count { it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 20.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row with Title, Active Status Badge, Reset Button, and Full EQ Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (activeEffectsCount > 0) MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Equalizer,
                            contentDescription = null,
                            tint = if (activeEffectsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Audio FX & Sound Engine",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (activeEffectsCount > 0) Color(0xFF00E676)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                            Text(
                                text = if (activeEffectsCount > 0) "$activeEffectsCount Effects Active" else "Direct Audio / Bypass",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (activeEffectsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset to Flat / Neutral Button
                    FilledTonalIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.resetAllAudioFx()
                            Toast.makeText(context, "Reset all audio effects to Flat", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "Reset to Flat",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Full Equalizer Screen Button
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenFullEqualizer()
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Full EQ", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // ==========================================
            // SECTION 1: EQ PROFILES (CIRCULAR CHIPS)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Sound Profiles",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (uiState.isEqEnabled) uiState.selectedEqPresetName else "Equalizer Disabled",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.isEqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Switch(
                            checked = uiState.isEqEnabled,
                            onCheckedChange = { isChecked ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setEqEnabled(isChecked)
                            },
                            modifier = Modifier.height(24.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                // Preset Pills (Scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PREBUILT_EQ_PRESETS.forEach { preset ->
                        val isSelected = uiState.isEqEnabled && uiState.selectedEqPresetName.equals(preset.name, ignoreCase = true)
                        val isSignaturePreset = preset.name.contains("Punch", ignoreCase = true) || 
                                                preset.name.contains("Bhais", ignoreCase = true) ||
                                                preset.name.contains("Harmonic", ignoreCase = true)

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // Smart logic: applying preset will automatically ensure isEqEnabled = true!
                                viewModel.applyPreset(
                                    preset.name,
                                    preset.levels,
                                    preset.bass,
                                    preset.vocal,
                                    preset.treble,
                                    preset.virtualizer
                                )
                            },
                            shape = CircleShape,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isSignaturePreset -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                if (isSelected || isSignaturePreset) 1.5.dp else 1.dp,
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isSignaturePreset -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSignaturePreset) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected || isSignaturePreset) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isSignaturePreset -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 2: POWER & BASS (BASS BOOST & DJ HAPTICS)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Power & Low-End",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                // Bass Boost Slider with auto-enable logic
                SmartSliderRow(
                    title = "Hardware Bass Boost",
                    subtitle = "Punchy Sub-Frequency Gain",
                    icon = Icons.Rounded.Speaker,
                    enabled = uiState.isBassBoostEnabled,
                    value = uiState.bassBoostLevel.toFloat(),
                    valueRange = 0f..1000f,
                    displayValue = "${(uiState.bassBoostLevel / 10)}%",
                    onToggle = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setBassBoostEnabled(isChecked)
                        if (isChecked && uiState.bassBoostLevel == 0) {
                            viewModel.setBassBoostLevel(500)
                        }
                    },
                    onValueChange = { newVal ->
                        // Automatically turns ON bass boost if dragged > 0
                        viewModel.setBassBoostLevel(newVal.toInt())
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                // DJ Bass Vibration Slider with mode chips
                SmartSliderRow(
                    title = "DJ Bass Vibration",
                    subtitle = "Haptic motor pulse on low drops",
                    icon = Icons.Rounded.Vibration,
                    enabled = uiState.isBassVibrationEnabled,
                    value = uiState.bassVibrationStrength.toFloat(),
                    valueRange = 10f..100f,
                    displayValue = "${uiState.bassVibrationStrength}%",
                    onToggle = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setBassVibrationEnabled(isChecked)
                    },
                    onValueChange = { newVal ->
                        viewModel.setBassVibrationStrength(newVal.toInt())
                    }
                )

                AnimatedVisibility(
                    visible = uiState.isBassVibrationEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BASS_VIBRATION_MODES.forEach { mode ->
                                val isSelected = uiState.bassVibrationMode.equals(mode, ignoreCase = true)
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setBassVibrationMode(mode)
                                    },
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    )
                                ) {
                                    Text(
                                        text = mode,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.testBassVibration()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.Vibration, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 3: SPATIAL & SOUNDSTAGE (3D, 8D, REVERB)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Spatial & Soundstage",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary
                )

                // 3D Spatial Virtualizer with auto-enable
                SmartSliderRow(
                    title = "3D Surround Virtualizer",
                    subtitle = "Stereo expansion & room width",
                    icon = Icons.Rounded.SurroundSound,
                    enabled = uiState.isVirtualizerEnabled,
                    value = uiState.virtualizerLevel.toFloat(),
                    valueRange = 0f..1000f,
                    displayValue = "${(uiState.virtualizerLevel / 10)}%",
                    onToggle = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setVirtualizerEnabled(isChecked)
                        if (isChecked && uiState.virtualizerLevel == 0) {
                            viewModel.setVirtualizerLevel(600)
                        }
                    },
                    onValueChange = { newVal ->
                        viewModel.setVirtualizerLevel(newVal.toInt())
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                // 8D Audio & Dolby Atmos Quick Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickToggleCard(
                        modifier = Modifier.weight(1f),
                        label = "8D Audio Rotation",
                        subtitle = "Binaural Motion",
                        icon = Icons.Rounded.Headphones,
                        isActive = uiState.is8dAudioEnabled,
                        onToggle = { isChecked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.set8dAudioEnabled(isChecked)
                            if (isChecked) {
                                Toast.makeText(context, "Best experienced with headphones 🎧", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    QuickToggleCard(
                        modifier = Modifier.weight(1f),
                        label = "Dolby Spatializer",
                        subtitle = "Acoustic Stage",
                        icon = Icons.Rounded.VolumeUp,
                        isActive = uiState.isDolbyEnabled,
                        onToggle = { isChecked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setDolbyEnabled(isChecked)
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                // Reverb Acoustic Environment Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Waves,
                                contentDescription = null,
                                tint = if (uiState.isReverbEnabled && uiState.reverbPreset > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Acoustic Reverb",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Switch(
                            checked = uiState.isReverbEnabled,
                            onCheckedChange = { isChecked ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setReverbEnabled(isChecked)
                                if (isChecked && uiState.reverbPreset == 0.toShort()) {
                                    viewModel.setReverbPreset(2.toShort()) // Medium room default
                                }
                            },
                            modifier = Modifier.height(24.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.secondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }

                    // Circular Reverb Environment Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUICK_REVERB_PRESETS.forEach { (presetId, presetName) ->
                            val isSelected = uiState.isReverbEnabled && (uiState.reverbPreset == presetId)
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Smart logic: selecting preset > 0 auto-enables reverb; selecting 0 disables it!
                                    viewModel.setReverbPreset(presetId)
                                },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = presetName,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 4: MASTER LOUDNESS BOOSTER
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Master Loudness & Gain",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )

                // Volume Gain Booster with auto-enable & safety advice
                SmartSliderRow(
                    title = "DSP Loudness Booster",
                    subtitle = "Boost beyond hardware limit (100% - 300%)",
                    icon = Icons.Rounded.Hearing,
                    enabled = uiState.isVolumeBoostEnabled && uiState.volumeBoostLevel > 100,
                    value = uiState.volumeBoostLevel.toFloat(),
                    valueRange = 100f..300f,
                    displayValue = "${uiState.volumeBoostLevel}%",
                    onToggle = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setVolumeBoostEnabled(isChecked)
                        if (isChecked && uiState.volumeBoostLevel == 100) {
                            viewModel.setVolumeBoostLevel(130)
                        } else if (!isChecked) {
                            viewModel.setVolumeBoostLevel(100)
                        }
                    },
                    onValueChange = { newVal ->
                        viewModel.setVolumeBoostLevel(newVal.toInt())
                    }
                )

                if (uiState.volumeBoostLevel > 180) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "High gain may cause distortion or speaker strain",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToggleCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onToggle(!isActive) },
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isActive) "Enabled" else subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SmartSliderRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onToggle: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (enabled) displayValue else "Off",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = enabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
        }
    }
}
