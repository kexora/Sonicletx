package com.kexora.soniclet.ui.components.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.SleepTimerOption
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ultra-Compact, 100% Opaque & Theme-Aware Sleep Timer Bottom Sheet.
 *
 * - Compact wrap-content height (~45%-52% screen height), zero oversized card boxes.
 * - Sleek 36dp CircleShape preset pills (15m, 30m, 45m, 60m, 90m, End of Track).
 * - Custom continuous pill slider (zero tick dots) with 5-minute haptic ticks and glowing thumb.
 * - Box-free "Finish current song first" toggle and full-pill primary CTA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDialog(
    currentOption: SleepTimerOption,
    remainingMs: Long,
    finishTrackBeforeSleep: Boolean,
    onSelectOption: (SleepTimerOption) -> Unit,
    onSetCustomTime: ((Int) -> Unit)? = null,
    onToggleFinishTrackBeforeSleep: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialMinutes = remember(currentOption, remainingMs) {
        when (currentOption) {
            SleepTimerOption.M15 -> 15
            SleepTimerOption.M30 -> 30
            SleepTimerOption.M45 -> 45
            SleepTimerOption.M60 -> 60
            SleepTimerOption.M90 -> 90
            SleepTimerOption.CUSTOM -> ((remainingMs / 60_000L).toInt()).coerceIn(5, 180).let {
                if (it < 5) 30 else ((it + 2) / 5 * 5).coerceIn(5, 180)
            }
            else -> 30
        }
    }

    var selectedMinutes by remember { mutableIntStateOf(initialMinutes) }
    var isEndOfTrackSelected by remember {
        mutableStateOf(currentOption == SleepTimerOption.END_OF_SONG)
    }

    val isTimerRunning = currentOption != SleepTimerOption.OFF &&
        (remainingMs > 0L || currentOption == SleepTimerOption.END_OF_SONG)

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = surfaceColor,
        contentColor = onSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .wrapContentHeight()
                .align(Alignment.CenterHorizontally)
                .drawBehind {
                    drawLine(
                        color = outlineVariant,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 18.dp)
                .testTag("sleep_timer_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Centered Drag Handle Pill
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(onSurfaceVariant.copy(alpha = 0.30f))
                )
            }

            // Compact Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "Fade out and pause music automatically",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Live Selected Duration Pill
                Surface(
                    shape = CircleShape,
                    color = primaryContainer
                ) {
                    Text(
                        text = if (isEndOfTrackSelected) "End of Track" else "$selectedMinutes min",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = primary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Active Countdown Strip (only visible when timer is running)
            if (isTimerRunning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(primaryContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE TIMER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = primary
                        )
                        Text(
                            text = if (currentOption == SleepTimerOption.END_OF_SONG) {
                                "After current track"
                            } else {
                                val totalSecs = (remainingMs / 1000L).coerceAtLeast(0L)
                                String.format(Locale.US, "%02d:%02d left", totalSecs / 60, totalSecs % 60)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentOption != SleepTimerOption.END_OF_SONG) {
                            Surface(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    val curMins = ((remainingMs + 59_999L) / 60_000L).toInt()
                                    val extended = (curMins + 10).coerceIn(5, 180)
                                    if (onSetCustomTime != null) {
                                        onSetCustomTime(extended)
                                    } else {
                                        onSelectOption(SleepTimerOption.CUSTOM)
                                    }
                                },
                                shape = CircleShape,
                                color = surfaceContainerHigh,
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("sleep_timer_extend_10m_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = onSurface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Extend +10m",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = onSurface
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelectOption(SleepTimerOption.OFF)
                                onDismiss()
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("sleep_timer_cancel_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancel Timer",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Sleek 36dp Pill Preset Chips (2 clean compact rows, zero boxed cards)
            val presetRows = remember {
                listOf(
                    listOf(
                        SleepPresetChip("15m", 15, SleepTimerOption.M15),
                        SleepPresetChip("30m", 30, SleepTimerOption.M30),
                        SleepPresetChip("45m", 45, SleepTimerOption.M45)
                    ),
                    listOf(
                        SleepPresetChip("60m", 60, SleepTimerOption.M60),
                        SleepPresetChip("90m", 90, SleepTimerOption.M90),
                        SleepPresetChip("End of Track", null, SleepTimerOption.END_OF_SONG)
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presetRows.forEach { rowChips ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowChips.forEach { chip ->
                            val isSelected = if (chip.minutes == null) {
                                isEndOfTrackSelected
                            } else {
                                !isEndOfTrackSelected && selectedMinutes == chip.minutes
                            }

                            Surface(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    if (chip.minutes == null) {
                                        isEndOfTrackSelected = true
                                    } else {
                                        isEndOfTrackSelected = false
                                        selectedMinutes = chip.minutes
                                    }
                                },
                                shape = CircleShape,
                                color = if (isSelected) primary else surfaceContainerHigh,
                                contentColor = if (isSelected) onPrimary else onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("sleep_preset_${chip.label.lowercase().replace(" ", "_")}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = chip.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Custom Continuous Pill Slider (ZERO tick dots, 5m..180m with 5m haptic ticks)
            Column(modifier = Modifier.fillMaxWidth()) {
                ContinuousPillDurationSlider(
                    minutes = selectedMinutes,
                    minMinutes = 5,
                    maxMinutes = 180,
                    stepMinutes = 5,
                    activeColor = primary,
                    inactiveTrackColor = surfaceContainerHigh,
                    onMinutesChange = { newMins ->
                        if (newMins != selectedMinutes || isEndOfTrackSelected) {
                            isEndOfTrackSelected = false
                            selectedMinutes = newMins
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("5m", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = onSurfaceVariant)
                    Text("60m", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = onSurfaceVariant)
                    Text("120m", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = onSurfaceVariant)
                    Text("180m", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = onSurfaceVariant)
                }
            }

            // Clean 0.5dp Hairline + Box-Free "Finish current song first" Toggle Row
            HorizontalDivider(thickness = 0.5.dp, color = outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleFinishTrackBeforeSleep(!finishTrackBeforeSleep)
                    }
                    .padding(vertical = 2.dp)
                    .testTag("sleep_timer_finish_song_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Finish current song first",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = onSurface
                    )
                    Text(
                        text = "Wait for active track to end before pausing",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = onSurfaceVariant
                    )
                }

                Switch(
                    checked = finishTrackBeforeSleep,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleFinishTrackBeforeSleep(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = onPrimary,
                        checkedTrackColor = primary,
                        uncheckedThumbColor = onSurfaceVariant,
                        uncheckedTrackColor = surfaceContainerHigh,
                        uncheckedBorderColor = outlineVariant
                    )
                )
            }

            // Full-Pill Primary Start CTA (48.dp height)
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (isEndOfTrackSelected) {
                        onSelectOption(SleepTimerOption.END_OF_SONG)
                    } else {
                        val matchingPreset = when (selectedMinutes) {
                            15 -> SleepTimerOption.M15
                            30 -> SleepTimerOption.M30
                            45 -> SleepTimerOption.M45
                            60 -> SleepTimerOption.M60
                            90 -> SleepTimerOption.M90
                            else -> null
                        }
                        if (matchingPreset != null) {
                            onSelectOption(matchingPreset)
                        } else if (onSetCustomTime != null) {
                            onSetCustomTime(selectedMinutes)
                        } else {
                            onSelectOption(SleepTimerOption.CUSTOM)
                        }
                    }
                    onDismiss()
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary,
                    contentColor = onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sleep_timer_start_cta")
            ) {
                Text(
                    text = if (isEndOfTrackSelected) {
                        "Pause After Current Track"
                    } else {
                        "Start $selectedMinutes Min Timer"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Custom Continuous Pill Slider with ZERO discrete tick dots.
 * - 6.dp pill track (`surfaceContainerHigh` inactive, `primary` active fill + soft glow).
 * - 18.dp glowing `primary` circular thumb that smoothly scales to 22.dp while dragging.
 * - Snaps cleanly to 5-minute increments (`minMinutes`..`maxMinutes`).
 */
@Composable
private fun ContinuousPillDurationSlider(
    minutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    stepMinutes: Int,
    activeColor: Color,
    inactiveTrackColor: Color,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(((minutes - minMinutes).toFloat() / (maxMinutes - minMinutes).toFloat()).coerceIn(0f, 1f)) }

    val displayedFraction = if (isDragging) {
        dragFraction
    } else {
        ((minutes - minMinutes).toFloat() / (maxMinutes - minMinutes).toFloat()).coerceIn(0f, 1f)
    }

    val thumbDiameter by animateDpAsState(
        targetValue = if (isDragging) 22.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sleep_slider_thumb_size"
    )

    fun fractionToSteppedMinutes(fraction: Float): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        val raw = minMinutes + clamped * (maxMinutes - minMinutes)
        val snapped = ((raw / stepMinutes).roundToInt() * stepMinutes).coerceIn(minMinutes, maxMinutes)
        return snapped
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(minMinutes, maxMinutes, stepMinutes) {
                detectTapGestures { offset ->
                    val usableWidth = (size.width - 24.dp.toPx()).coerceAtLeast(1f)
                    val fraction = ((offset.x - 12.dp.toPx()) / usableWidth).coerceIn(0f, 1f)
                    dragFraction = fraction
                    onMinutesChange(fractionToSteppedMinutes(fraction))
                }
            }
            .pointerInput(minMinutes, maxMinutes, stepMinutes) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val usableWidth = (size.width - 24.dp.toPx()).coerceAtLeast(1f)
                        val fraction = ((offset.x - 12.dp.toPx()) / usableWidth).coerceIn(0f, 1f)
                        dragFraction = fraction
                        onMinutesChange(fractionToSteppedMinutes(fraction))
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val usableWidth = (size.width - 24.dp.toPx()).coerceAtLeast(1f)
                        val fraction = ((change.position.x - 12.dp.toPx()) / usableWidth).coerceIn(0f, 1f)
                        dragFraction = fraction
                        onMinutesChange(fractionToSteppedMinutes(fraction))
                    }
                )
            }
            .testTag("sleep_timer_custom_slider"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val horizontalPad = 12.dp.toPx()
            val trackHeight = 6.dp.toPx()
            val trackRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            val trackWidth = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
            val centerY = size.height / 2f
            val trackTop = centerY - trackHeight / 2f

            // Inactive Pill Track
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(horizontalPad, trackTop),
                size = Size(trackWidth, trackHeight),
                cornerRadius = trackRadius
            )

            val thumbCenterX = horizontalPad + trackWidth * displayedFraction
            val activeWidth = (thumbCenterX - horizontalPad).coerceAtLeast(trackHeight)

            // Subtle ambient glow under active track
            drawRoundRect(
                color = activeColor.copy(alpha = 0.22f),
                topLeft = Offset(horizontalPad, trackTop - 2.dp.toPx()),
                size = Size(activeWidth, trackHeight + 4.dp.toPx()),
                cornerRadius = CornerRadius((trackHeight + 4.dp.toPx()) / 2f, (trackHeight + 4.dp.toPx()) / 2f)
            )

            // Crisp Active Pill Track (ZERO tick dots)
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(horizontalPad, trackTop),
                size = Size(activeWidth, trackHeight),
                cornerRadius = trackRadius
            )

            // Soft Halo + Circular Glowing Thumb
            val thumbRadius = thumbDiameter.toPx() / 2f
            drawCircle(
                color = activeColor.copy(alpha = if (isDragging) 0.30f else 0.18f),
                radius = thumbRadius + 5.dp.toPx(),
                center = Offset(thumbCenterX, centerY)
            )
            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = Offset(thumbCenterX, centerY)
            )
        }
    }
}

private data class SleepPresetChip(
    val label: String,
    val minutes: Int?,
    val option: SleepTimerOption
)
