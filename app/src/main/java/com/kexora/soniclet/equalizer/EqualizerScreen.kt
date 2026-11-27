package com.kexora.soniclet.equalizer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.CustomEqPreset
import com.kexora.soniclet.settings.*
import com.kexora.soniclet.ui.components.common.*

private val REVERB_PRESETS_DATA = listOf(
    0.toShort() to "None",
    1.toShort() to "Small Room",
    2.toShort() to "Medium Room",
    3.toShort() to "Large Room",
    4.toShort() to "Medium Hall",
    5.toShort() to "Large Hall",
    6.toShort() to "Plate"
)

private val BASS_VIBRATION_MODES = listOf("DJ Club Bass", "Beat Drop Subwoofer", "Extreme Rave")
private val DOLBY_PROFILES = listOf("Dolby Atmos Dynamic", "Cinema Surround", "Music Studio", "Night Clarity")

@Composable
fun EqualizerSettingsSubPage(
    isEqEnabled: Boolean,
    isReverbEnabled: Boolean,
    isBassBoostEnabled: Boolean,
    isVirtualizerEnabled: Boolean,
    isVolumeBoostEnabled: Boolean,
    isDolbyEnabled: Boolean,
    isLosslessEnabled: Boolean,
    is8dAudioEnabled: Boolean,
    onToggleEq: (Boolean) -> Unit,
    onToggleReverb: (Boolean) -> Unit,
    onToggleBassBoost: (Boolean) -> Unit,
    onToggleVirtualizer: (Boolean) -> Unit,
    onToggleVolumeBoost: (Boolean) -> Unit,
    onToggleDolby: (Boolean) -> Unit,
    onToggleLossless: (Boolean) -> Unit,
    onToggle8dAudio: (Boolean) -> Unit,
    isBassVibrationEnabled: Boolean = false,
    bassVibrationStrength: Int = 80,
    bassVibrationMode: String = "DJ Club Bass",
    onToggleBassVibration: (Boolean) -> Unit = {},
    onSetBassVibrationStrength: (Int) -> Unit = {},
    onSetBassVibrationMode: (String) -> Unit = {},
    onTestBassVibration: () -> Unit = {},
    eqBandLevels: List<Int>,
    eqBandFrequencies: List<String>,
    selectedEqPresetName: String,
    customEqPresets: List<CustomEqPreset>,
    bassBoostLevel: Int,
    vocalBoostLevel: Int,
    trebleBoostLevel: Int,
    virtualizerLevel: Int,
    volumeBoostLevel: Int = 100,
    reverbPreset: Short,
    onSetEqualizerBandLevel: (Int, Int) -> Unit,
    onApplyPreset: (String, List<Int>, Int, Int, Int, Int) -> Unit,
    onResetEqualizer: () -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onSetBassBoostLevel: (Int) -> Unit,
    onSetVocalBoostLevel: (Int) -> Unit,
    onSetTrebleBoostLevel: (Int) -> Unit,
    onSetVirtualizerLevel: (Int) -> Unit,
    onSetVolumeBoostLevel: (Int) -> Unit = {},
    onSetReverbPreset: (Short) -> Unit,
    onResetAllAudioFx: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var selectedDolbyProfile by remember { mutableStateOf("Dolby Atmos Dynamic") }

    // Count of active audio sound processing features
    val activeEffectsCount = remember(
        isEqEnabled,
        isBassBoostEnabled,
        bassBoostLevel,
        isBassVibrationEnabled,
        isVirtualizerEnabled,
        virtualizerLevel,
        is8dAudioEnabled,
        isDolbyEnabled,
        isLosslessEnabled,
        isReverbEnabled,
        reverbPreset,
        isVolumeBoostEnabled,
        volumeBoostLevel
    ) {
        listOf(
            isEqEnabled,
            isBassBoostEnabled && bassBoostLevel > 0,
            isBassVibrationEnabled,
            isVirtualizerEnabled && virtualizerLevel > 0,
            is8dAudioEnabled,
            isDolbyEnabled,
            isLosslessEnabled,
            isReverbEnabled && reverbPreset > 0,
            isVolumeBoostEnabled && volumeBoostLevel > 100
        ).count { it }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ==========================================
        // TOP HERO STATUS & RESET CARD (SAME AS EQ SHEET)
        // ==========================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
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
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Studio Sound Engine",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
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
                                text = if (activeEffectsCount > 0) "$activeEffectsCount Processors Active" else "Direct Audio / Bypass",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (activeEffectsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Reset All to Flat / Neutral Button
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (onResetAllAudioFx != null) {
                            onResetAllAudioFx()
                        } else {
                            onResetEqualizer()
                            onToggleBassBoost(false)
                            onSetBassBoostLevel(0)
                            onToggleVirtualizer(false)
                            onSetVirtualizerLevel(0)
                            onToggle8dAudio(false)
                            onToggleDolby(false)
                            onToggleReverb(false)
                            onSetReverbPreset(0.toShort())
                            onToggleVolumeBoost(false)
                            onSetVolumeBoostLevel(100)
                            onToggleBassVibration(false)
                        }
                        Toast.makeText(context, "Reset all audio effects to Flat", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Rounded.RestartAlt,
                        contentDescription = "Reset All Effects",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ==========================================
        // SECTION 1: GRAPHIC EQUALIZER & PROFILES
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Switch and Reset EQ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEqEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = if (isEqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Graphic Equalizer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEqEnabled) "Active • $selectedEqPresetName" else "Off • Bypassed signal",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isEqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isEqEnabled) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResetEqualizer()
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Reset EQ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Switch(
                        checked = isEqEnabled,
                        onCheckedChange = { isChecked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleEq(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            // Built-In Profiles Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Built-in Sound Profiles",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PREBUILT_EQ_PRESETS.forEach { preset ->
                        val isSelected = isEqEnabled && selectedEqPresetName.equals(preset.name, ignoreCase = true)
                        val isSignaturePreset = preset.name.contains("Punch", ignoreCase = true) ||
                                preset.name.contains("MUH", ignoreCase = true) ||
                                preset.name.contains("Harmonic", ignoreCase = true)

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // Smart logic: applying preset will automatically ensure isEqEnabled = true!
                                if (!isEqEnabled) {
                                    onToggleEq(true)
                                }
                                onApplyPreset(
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

            // Custom Profiles Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Profiles",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showSavePresetDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Current", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                if (customEqPresets.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        customEqPresets.forEach { customPreset ->
                            val isSelected = isEqEnabled && selectedEqPresetName.equals(customPreset.name, ignoreCase = true)
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (!isEqEnabled) {
                                        onToggleEq(true)
                                    }
                                    onApplyPreset(
                                        customPreset.name,
                                        customPreset.levels,
                                        customPreset.bass,
                                        customPreset.vocal,
                                        customPreset.treble,
                                        customPreset.virtualizer
                                    )
                                },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = customPreset.name,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { onDeleteCustomPreset(customPreset.name) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time Bezier EQ Curve Visualizer
            EqCurveVisualizer(
                levels = eqBandLevels,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(8.dp)
            )

            // Studio Faders ("डंटी-डंटी")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                eqBandLevels.forEachIndexed { index, level ->
                    val freq = eqBandFrequencies.getOrElse(index) { "${index + 1}" }
                    Column(
                        modifier = Modifier.width(58.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (level > 0) "+${level / 100}" else "${level / 100}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (level != 0 && isEqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        StudioFader(
                            value = level.toFloat(),
                            onValueChange = { newLevel ->
                                if (!isEqEnabled) {
                                    onToggleEq(true)
                                }
                                onSetEqualizerBandLevel(index, newLevel.toInt())
                            },
                            valueRange = -1500f..1500f,
                            modifier = Modifier
                                .height(175.dp)
                                .fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = freq,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Vocal Presence & Treble Air Sliders
            SmartSliderRow(
                title = "Vocal Booster & Clarity",
                subtitle = "Enhance singer presence and dialogue clarity",
                icon = Icons.Rounded.RecordVoiceOver,
                enabled = isEqEnabled,
                value = vocalBoostLevel.toFloat(),
                valueRange = -1000f..1000f,
                displayValue = if (vocalBoostLevel > 0) "+${vocalBoostLevel / 10}%" else "${vocalBoostLevel / 10}%",
                onToggle = { isChecked ->
                    if (isChecked && !isEqEnabled) onToggleEq(true)
                    onSetVocalBoostLevel(if (isChecked) 400 else 0)
                },
                onValueChange = {
                    if (!isEqEnabled) onToggleEq(true)
                    onSetVocalBoostLevel(it.toInt())
                }
            )

            SmartSliderRow(
                title = "Treble Brightness & Air",
                subtitle = "Crisp cymbals, airiness, and high-end detail",
                icon = Icons.Rounded.Waves,
                enabled = isEqEnabled,
                value = trebleBoostLevel.toFloat(),
                valueRange = 0f..1000f,
                displayValue = "${(trebleBoostLevel / 10)}%",
                onToggle = { isChecked ->
                    if (isChecked && !isEqEnabled) onToggleEq(true)
                    onSetTrebleBoostLevel(if (isChecked) 500 else 0)
                },
                onValueChange = {
                    if (!isEqEnabled) onToggleEq(true)
                    onSetTrebleBoostLevel(it.toInt())
                }
            )
        }

        // ==========================================
        // SECTION 2: POWER & LOW-END (BASS BOOST & DJ HAPTICS)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Power & Low-End",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            // Hardware Bass Boost
            SmartSliderRow(
                title = "Hardware Bass Boost",
                subtitle = "Punchy sub-bass resonance, warmth & kick",
                icon = Icons.Rounded.Speaker,
                enabled = isBassBoostEnabled,
                value = bassBoostLevel.toFloat(),
                valueRange = 0f..1000f,
                displayValue = "${(bassBoostLevel / 10)}%",
                onToggle = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleBassBoost(isChecked)
                    if (isChecked && bassBoostLevel == 0) {
                        onSetBassBoostLevel(500)
                    }
                },
                onValueChange = { newVal ->
                    if (newVal > 0 && !isBassBoostEnabled) {
                        onToggleBassBoost(true)
                    }
                    onSetBassBoostLevel(newVal.toInt())
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // DJ Bass Vibration (Physical Tactile Kick)
            SmartSliderRow(
                title = "DJ Bass Vibration",
                subtitle = "Physical device haptic kick on low bass drops",
                icon = Icons.Rounded.Vibration,
                enabled = isBassVibrationEnabled,
                value = bassVibrationStrength.toFloat(),
                valueRange = 10f..100f,
                displayValue = "$bassVibrationStrength%",
                onToggle = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleBassVibration(isChecked)
                },
                onValueChange = { newVal ->
                    if (!isBassVibrationEnabled) onToggleBassVibration(true)
                    onSetBassVibrationStrength(newVal.toInt())
                }
            )

            AnimatedVisibility(
                visible = isBassVibrationEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Vibration Rhythm Style",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                                val isSelected = bassVibrationMode.equals(mode, ignoreCase = true)
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSetBassVibrationMode(mode)
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
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                                onTestBassVibration()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.Vibration, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Kick", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // ==========================================
        // SECTION 3: SPATIAL & SOUNDSTAGE (3D, 8D, DOLBY, REVERB)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Spatial & Soundstage",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.secondary
            )

            // 3D Spatial Virtualizer
            SmartSliderRow(
                title = "3D Surround Virtualizer",
                subtitle = "Stereo expansion & room soundstage width",
                icon = Icons.Rounded.SurroundSound,
                enabled = isVirtualizerEnabled,
                value = virtualizerLevel.toFloat(),
                valueRange = 0f..1000f,
                displayValue = "${(virtualizerLevel / 10)}%",
                onToggle = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleVirtualizer(isChecked)
                    if (isChecked && virtualizerLevel == 0) {
                        onSetVirtualizerLevel(600)
                    }
                },
                onValueChange = { newVal ->
                    if (newVal > 0 && !isVirtualizerEnabled) {
                        onToggleVirtualizer(true)
                    }
                    onSetVirtualizerLevel(newVal.toInt())
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // 8D Audio Rotation & Dolby Spatializer Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickToggleCard(
                    modifier = Modifier.weight(1f),
                    label = "8D Audio Rotation",
                    subtitle = "Binaural Motion",
                    icon = Icons.Rounded.Headphones,
                    isActive = is8dAudioEnabled,
                    onToggle = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle8dAudio(isChecked)
                        if (isChecked) {
                            Toast.makeText(context, "Best experienced with headphones 🎧", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                QuickToggleCard(
                    modifier = Modifier.weight(1f),
                    label = "Dolby Surround",
                    subtitle = "Cinema Dynamics",
                    icon = Icons.Rounded.TheaterComedy,
                    isActive = isDolbyEnabled,
                    onToggle = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleDolby(isChecked)
                    }
                )
            }

            AnimatedVisibility(
                visible = isDolbyEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Dolby Audio Profile",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DOLBY_PROFILES.forEach { profile ->
                            val isSelected = (selectedDolbyProfile == profile)
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedDolbyProfile = profile
                                },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = profile,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // Reverb Environmental Acoustic Simulator
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
                            tint = if (isReverbEnabled && reverbPreset > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Environmental Reverb",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Switch(
                        checked = isReverbEnabled,
                        onCheckedChange = { isChecked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleReverb(isChecked)
                            if (isChecked && reverbPreset == 0.toShort()) {
                                onSetReverbPreset(2.toShort())
                            }
                        },
                        modifier = Modifier.height(24.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.secondary,
                            checkedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    REVERB_PRESETS_DATA.forEach { (presetId, presetName) ->
                        val isSelected = isReverbEnabled && (reverbPreset == presetId)
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (presetId == 0.toShort()) {
                                    onSetReverbPreset(0.toShort())
                                    onToggleReverb(false)
                                } else {
                                    if (!isReverbEnabled) onToggleReverb(true)
                                    onSetReverbPreset(presetId)
                                }
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
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
        // SECTION 4: MASTER LOUDNESS & HI-RES ENGINE
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Master Loudness & Studio Fidelity",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
            )

            // Lossless Hi-Res Audio Engine
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleLossless(!isLosslessEnabled)
                },
                shape = RoundedCornerShape(16.dp),
                color = if (isLosslessEnabled) Color(0xFF00E676).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isLosslessEnabled) Color(0xFF00E676).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Lossless Hi-Res Audio Engine",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isLosslessEnabled) "32-Bit Float Master • Zero dithering or compression" else "Standard playback format",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (isLosslessEnabled) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Switch(
                        checked = isLosslessEnabled,
                        onCheckedChange = { isChecked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleLossless(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E676),
                            checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.35f)
                        )
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // Volume Booster (Hardware Loudness Gain)
            SmartSliderRow(
                title = "DSP Loudness Booster",
                subtitle = "Hardware gain boost past 100% (Up to 300%)",
                icon = Icons.Rounded.VolumeUp,
                enabled = isVolumeBoostEnabled && volumeBoostLevel > 100,
                value = volumeBoostLevel.toFloat(),
                valueRange = 100f..300f,
                displayValue = "${volumeBoostLevel}%",
                onToggle = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleVolumeBoost(isChecked)
                    if (isChecked && volumeBoostLevel == 100) {
                        onSetVolumeBoostLevel(130)
                    } else if (!isChecked) {
                        onSetVolumeBoostLevel(100)
                    }
                },
                onValueChange = { newVal ->
                    if (newVal > 100 && !isVolumeBoostEnabled) {
                        onToggleVolumeBoost(true)
                    }
                    onSetVolumeBoostLevel(newVal.toInt())
                }
            )

            if (volumeBoostLevel > 180) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "High gain may cause speaker distortion or hearing strain",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(com.kexora.soniclet.LocalPlayerBottomPadding.current + 8.dp))
    }

    // Dialog: Save Custom Preset
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.BookmarkAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Save Custom Preset",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Save current 5-band levels, bass boost, and soundstage settings:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("Preset Name") },
                        placeholder = { Text("e.g. My Bass Punch") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            onSaveCustomPreset(newPresetName.trim())
                            showSavePresetDialog = false
                            newPresetName = ""
                        }
                    },
                    shape = CircleShape
                ) {
                    Text("Save Preset")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSavePresetDialog = false },
                    shape = CircleShape
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// -------------------------------------------------------------
// REUSABLE MODERN COMPONENTS (SAME AS EQ SHEET)
// -------------------------------------------------------------

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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
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
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// PREMIUM STUDIO MIXING COMPONENTS
// -------------------------------------------------------------

@Composable
fun EqCurveVisualizer(levels: List<Int>, modifier: Modifier = Modifier) {
    if (levels.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val maxLevel = 1500f
        val minLevel = -1500f
        val range = maxLevel - minLevel

        // Zero dB Reference Line
        val zeroY = height / 2f
        drawLine(
            color = Color.Gray.copy(alpha = 0.25f),
            start = Offset(0f, zeroY),
            end = Offset(width, zeroY),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )

        val stepX = width / (levels.size.coerceAtLeast(2) - 1).toFloat()
        val points = levels.mapIndexed { index, level ->
            val x = index * stepX
            val normalized = (level - minLevel) / range
            val y = height - (normalized * height)
            Offset(x, y)
        }

        // Build smooth bezier curve path
        val path = Path()
        path.moveTo(points.first().x, points.first().y)

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cx1 = p0.x + (p1.x - p0.x) / 2f
            val cy1 = p0.y
            val cx2 = p0.x + (p1.x - p0.x) / 2f
            val cy2 = p1.y
            path.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
        }

        // Glow stroke layers
        drawPath(
            path = path,
            color = primaryColor.copy(alpha = 0.15f),
            style = Stroke(width = 24f, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = primaryColor.copy(alpha = 0.35f),
            style = Stroke(width = 10f, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        // Draw frequency node dots
        points.forEach { pt ->
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = pt
            )
            drawCircle(
                color = primaryColor,
                radius = 4f,
                center = pt
            )
        }
    }
}

@Composable
fun StudioFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val thumbColor = MaterialTheme.colorScheme.onSurface

    var dragStartY by remember { mutableStateOf(0f) }
    var startValue by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalized = 1f - (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                    val newValue = valueRange.start + normalized * (valueRange.endInclusive - valueRange.start)
                    val finalValue = if (kotlin.math.abs(newValue) < 120f) 0f else newValue
                    onValueChange(finalValue)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartY = offset.y
                        startValue = value
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val deltaY = change.position.y - dragStartY
                        val valuePerPixel = (valueRange.endInclusive - valueRange.start) / size.height.toFloat()
                        val newValue = startValue - (deltaY * valuePerPixel)
                        val clampedValue = newValue.coerceIn(valueRange)
                        val finalValue = if (kotlin.math.abs(clampedValue) < 80f) 0f else clampedValue
                        onValueChange(finalValue)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background track line
            drawLine(
                color = trackColor,
                start = Offset(w / 2f, 10f),
                end = Offset(w / 2f, h - 10f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )

            // Zero dB tick mark
            drawLine(
                color = trackColor,
                start = Offset(w / 2f - 14f, h / 2f),
                end = Offset(w / 2f + 14f, h / 2f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )

            // Calculate active thumb position
            val range = valueRange.endInclusive - valueRange.start
            val normalized = (value - valueRange.start) / range
            val thumbY = (h - 20f) - (normalized * (h - 20f)) + 10f

            // Active track fill
            val zeroY = h / 2f
            if (value != 0f) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.85f),
                    start = Offset(w / 2f, zeroY),
                    end = Offset(w / 2f, thumbY),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }

            // Thumb Pill Shape
            val thumbWidth = 40f
            val thumbHeight = 16f
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(w / 2f - thumbWidth / 2f, thumbY - thumbHeight / 2f),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            // Thumb inner glow line
            drawLine(
                color = if (value != 0f) primaryColor else Color.Gray,
                start = Offset(w / 2f - thumbWidth / 2.5f, thumbY),
                end = Offset(w / 2f + thumbWidth / 2.5f, thumbY),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }
    }
}

// -------------------------------------------------------------
// DATA MODELS & PRESETS
// -------------------------------------------------------------

data class BuiltInEqPreset(
    val name: String,
    val levels: List<Int>,
    val bass: Int = 0,
    val vocal: Int = 0,
    val treble: Int = 0,
    val virtualizer: Int = 0
)

val PREBUILT_EQ_PRESETS = listOf(
    BuiltInEqPreset("MUH Harmonic Punch ✨", listOf(1400, 950, 200, 100, -200), 1000, 250, 150, 450),
    BuiltInEqPreset("Flat / Normal", listOf(0, 0, 0, 0, 0), 0, 0, 0, 0),
    BuiltInEqPreset("Bass Heavy", listOf(900, 600, 100, -200, -400), 800, 0, 0, 300),
    BuiltInEqPreset("Deep Sub-Bass", listOf(1200, 800, -100, -300, -500), 1000, 0, 0, 200),
    BuiltInEqPreset("Vocal Booster", listOf(-300, 100, 1000, 700, 200), 0, 800, 300, 100),
    BuiltInEqPreset("Treble Clarity", listOf(-400, -200, 200, 800, 1100), 0, 200, 850, 300),
    BuiltInEqPreset("Rock", listOf(700, 400, -200, 400, 800), 500, 0, 500, 400),
    BuiltInEqPreset("Pop", listOf(-200, 400, 700, 500, -100), 300, 400, 300, 300),
    BuiltInEqPreset("Electronic / EDM", listOf(900, 500, 0, 400, 900), 750, 0, 700, 600),
    BuiltInEqPreset("Hip-Hop", listOf(1000, 700, 100, 200, 400), 900, 100, 200, 400),
    BuiltInEqPreset("Acoustic", listOf(500, 300, 200, 500, 600), 200, 300, 500, 400),
    BuiltInEqPreset("Jazz", listOf(400, 200, 100, 300, 500), 300, 200, 400, 500),
    BuiltInEqPreset("Classical", listOf(500, 300, -200, 400, 600), 200, 0, 500, 600),
    BuiltInEqPreset("Heavy Metal", listOf(800, 500, -400, 600, 900), 700, -200, 800, 500),
    BuiltInEqPreset("Dance & Club", listOf(800, 600, 200, 500, 700), 800, 0, 600, 500),
    BuiltInEqPreset("Soft & Late Night", listOf(300, 100, -100, -200, -400), 200, 0, 0, 200),
    BuiltInEqPreset("Party & Loudness", listOf(1000, 700, 300, 600, 1000), 850, 200, 850, 700)
)
