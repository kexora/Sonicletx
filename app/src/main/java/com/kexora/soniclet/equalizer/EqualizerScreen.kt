package com.kexora.soniclet.equalizer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
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

/**
 * Box-free, edge-to-edge Studio Equalizer & Sound FX Screen.
 * Preserves 100% of DSP, EQ curve visualizer, vertical studio faders, bass haptics, 8D, Dolby, reverb, and loudness controls.
 */
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
    ) {
        // ==========================================
        // BORDERLESS STUDIO SOUND ENGINE STATUS STRIP
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Equalizer,
                    contentDescription = null,
                    tint = if (activeEffectsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "Studio Sound Engine",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (activeEffectsCount > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                        )
                        Text(
                            text = if (activeEffectsCount > 0) "$activeEffectsCount Processors Active" else "Direct Audio / Bypass",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = if (activeEffectsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
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
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset All Effects",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        // ==========================================
        // SECTION 1: GRAPHIC EQUALIZER & PROFILES
        // ==========================================
        EdgeSectionHeader(title = "GRAPHIC EQUALIZER")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleEq(!isEqEnabled)
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (isEqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Equalizer DSP",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEqEnabled) "Active • $selectedEqPresetName" else "Off • Pure unmodified signal",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = if (isEqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEqEnabled) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResetEqualizer()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Flat",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Switch(
                    checked = isEqEnabled,
                    onCheckedChange = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleEq(isChecked)
                    },
                    colors = sonicletSwitchColors()
                )
            }
        }

        // Built-In Profiles Edge-to-Edge LazyRow
        Text(
            text = "Built-in Sound Profiles",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(PREBUILT_EQ_PRESETS) { preset ->
                val isSelected = isEqEnabled && selectedEqPresetName.equals(preset.name, ignoreCase = true)
                val isSignaturePreset = preset.name.contains("Punch", ignoreCase = true) ||
                    preset.name.contains("MUH", ignoreCase = true) ||
                    preset.name.contains("Harmonic", ignoreCase = true)

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        isSignaturePreset -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        width = if (isSelected || isSignaturePreset) 1.dp else 0.6.dp,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isSignaturePreset -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isSignaturePreset) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected || isSignaturePreset) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isSignaturePreset -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        // Custom Profiles Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Custom Profiles",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                onClick = { showSavePresetDialog = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Save Current",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (customEqPresets.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(customEqPresets) { customPreset ->
                    val isSelected = isEqEnabled && selectedEqPresetName.equals(customPreset.name, ignoreCase = true)
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (!isEqEnabled) onToggleEq(true)
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
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            0.8.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = customPreset.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { onDeleteCustomPreset(customPreset.name) },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Delete Preset",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Borderless Real-Time Bezier EQ Curve Visualizer directly over canvas
        EqCurveVisualizer(
            levels = eqBandLevels,
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // Vertical Studio Faders with generous edge padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            eqBandLevels.forEachIndexed { index, level ->
                val freq = eqBandFrequencies.getOrElse(index) { "${index + 1}" }
                Column(
                    modifier = Modifier.width(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (level > 0) "+${level / 100}" else "${level / 100}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (level != 0 && isEqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

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
                            .height(170.dp)
                            .fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = freq,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

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

        EdgeHairlineDivider()

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

        // ==========================================
        // SECTION 2: POWER & LOW-END
        // ==========================================
        EdgeSectionHeader(title = "POWER & LOW-END")

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

        EdgeHairlineDivider()

        SmartSliderRow(
            title = "DJ Bass Vibration",
            subtitle = "Physical device haptic kick synced to low bass drops",
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vibration Rhythm Style",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTestBassVibration()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Vibration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Test Kick",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(BASS_VIBRATION_MODES) { mode ->
                        val isSelected = bassVibrationMode.equals(mode, ignoreCase = true)
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetBassVibrationMode(mode)
                            },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isSelected) null else BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = mode,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // SECTION 3: SPATIAL & SOUNDSTAGE
        // ==========================================
        EdgeSectionHeader(title = "SPATIAL & SOUNDSTAGE")

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

        EdgeHairlineDivider()

        // 8D Audio Rotation & Dolby Spatializer Sleek Pill Toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickTogglePill(
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

            QuickTogglePill(
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Dolby Audio Profile",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DOLBY_PROFILES) { profile ->
                        val isSelected = selectedDolbyProfile == profile
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedDolbyProfile = profile
                            },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isSelected) null else BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = profile,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        EdgeHairlineDivider()

        // Environmental Reverb
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val next = !isReverbEnabled
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleReverb(next)
                    if (next && reverbPreset == 0.toShort()) {
                        onSetReverbPreset(2.toShort())
                    }
                }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Waves,
                    contentDescription = null,
                    tint = if (isReverbEnabled && reverbPreset > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Environmental Reverb",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Acoustic room & hall ambience simulator",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                colors = sonicletSwitchColors()
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(REVERB_PRESETS_DATA) { (presetId, presetName) ->
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
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (isSelected) null else BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = presetName,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // ==========================================
        // SECTION 4: MASTER LOUDNESS & HI-RES ENGINE
        // ==========================================
        EdgeSectionHeader(title = "MASTER LOUDNESS & STUDIO FIDELITY")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleLossless(!isLosslessEnabled)
                }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = if (isLosslessEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Lossless Hi-Res Audio Engine",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLosslessEnabled) "32-Bit Float Master • Zero dithering or compression" else "Standard playback format",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = if (isLosslessEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = isLosslessEnabled,
                onCheckedChange = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleLossless(isChecked)
                },
                colors = sonicletSwitchColors()
            )
        }

        EdgeHairlineDivider()

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
            EdgeInfoBanner(
                icon = Icons.Rounded.Warning,
                message = "High gain above 180% may cause speaker distortion or hearing strain.",
                accentColor = MaterialTheme.colorScheme.error
            )
        }

        EdgeSettingsBottomSpacer()
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
                        text = "Save current band levels, bass boost, and soundstage settings:",
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

@Composable
private fun QuickTogglePill(
    label: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onToggle(!isActive) },
        shape = CircleShape,
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            0.8.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isActive) "Active" else subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (enabled) displayValue else "Off",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = sonicletSwitchColors()
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
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp)
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
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val nodeHaloColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val maxLevel = 1500f
        val minLevel = -1500f
        val range = maxLevel - minLevel

        val zeroY = height / 2f
        drawLine(
            color = baselineColor,
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

        drawPath(
            path = path,
            color = primaryColor.copy(alpha = 0.14f),
            style = Stroke(width = 24f, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = primaryColor.copy(alpha = 0.34f),
            style = Stroke(width = 10f, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(
                color = nodeHaloColor,
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

            drawLine(
                color = trackColor,
                start = Offset(w / 2f, 10f),
                end = Offset(w / 2f, h - 10f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )

            drawLine(
                color = trackColor,
                start = Offset(w / 2f - 14f, h / 2f),
                end = Offset(w / 2f + 14f, h / 2f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )

            val range = valueRange.endInclusive - valueRange.start
            val normalized = (value - valueRange.start) / range
            val thumbY = (h - 20f) - (normalized * (h - 20f)) + 10f

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

            val thumbWidth = 40f
            val thumbHeight = 16f
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(w / 2f - thumbWidth / 2f, thumbY - thumbHeight / 2f),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
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
