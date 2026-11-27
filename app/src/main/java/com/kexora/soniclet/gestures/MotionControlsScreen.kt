package com.kexora.soniclet.gestures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.settings.*
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter

/**
 * Box-free, edge-to-edge Hardware Motion & Sensors Settings Screen.
 */
@Composable
fun MotionSettingsSubPage(
    isShakeEnabled: Boolean,
    onToggleShakeEnabled: (Boolean) -> Unit,
    isFlipToPauseEnabled: Boolean,
    flipToPauseAction: String,
    isWristTwistEnabled: Boolean,
    wristTwistAction: String,
    isPocketModeEnabled: Boolean,
    pocketModeAction: String,
    shakeAction: String,
    isTiltToFastForwardEnabled: Boolean,
    isAirWaveEnabled: Boolean,
    airWaveAction: String,
    isPickUpToPlayEnabled: Boolean,
    onToggleFlipToPause: (Boolean) -> Unit,
    onSetFlipToPauseAction: (String) -> Unit,
    onToggleWristTwist: (Boolean) -> Unit,
    onSetWristTwistAction: (String) -> Unit,
    onTogglePocketMode: (Boolean) -> Unit,
    onSetPocketModeAction: (String) -> Unit,
    onSetShakeAction: (String) -> Unit,
    onToggleTiltToFastForward: (Boolean) -> Unit,
    onToggleAirWave: (Boolean) -> Unit,
    onSetAirWaveAction: (String) -> Unit,
    onTogglePickUpToPlay: (Boolean) -> Unit,
    onOpenMotionManual: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Borderless Emerald-Tinted Battery Efficiency Strip
        EdgeInfoBanner(
            icon = Icons.Rounded.BatteryChargingFull,
            message = "Sensors run in low-power mode and activate only while music is playing."
        )

        // Section 1: Dynamic Motion Gestures
        EdgeSectionHeader(title = "SMART DEVICE SENSORS")

        FluidMotionToggleRow(
            icon = Icons.Rounded.ScreenRotation,
            iconTint = Color(0xFFF97316),
            title = "Flip Face-Down",
            subtitle = "Place phone screen down to $flipToPauseAction",
            isChecked = isFlipToPauseEnabled,
            onToggle = onToggleFlipToPause
        )

        EdgeHairlineDivider()

        FluidMotionToggleRow(
            icon = Icons.Rounded.Watch,
            iconTint = Color(0xFF3B82F6),
            title = "Wrist Twist",
            subtitle = "Twist wrist back and forth to $wristTwistAction",
            isChecked = isWristTwistEnabled,
            onToggle = onToggleWristTwist
        )

        EdgeHairlineDivider()

        FluidMotionToggleRow(
            icon = Icons.Rounded.Security,
            iconTint = Color(0xFF10B981),
            title = "Smart Pocket Mode",
            subtitle = "Auto-pause & lock controls when phone is in pocket",
            isChecked = isPocketModeEnabled,
            onToggle = onTogglePocketMode
        )

        EdgeHairlineDivider()

        FluidMotionToggleRow(
            icon = Icons.Rounded.Vibration,
            iconTint = Color(0xFFEC4899),
            title = "Hard Shake",
            subtitle = "Firm shake to $shakeAction",
            isChecked = isShakeEnabled,
            onToggle = onToggleShakeEnabled
        )

        EdgeHairlineDivider()

        FluidMotionToggleRow(
            icon = Icons.Rounded.CompareArrows,
            iconTint = Color(0xFF06B6D4),
            title = "Tilt to Fast-Forward",
            subtitle = "Tilt phone left/right in player to scrub track",
            isChecked = isTiltToFastForwardEnabled,
            onToggle = onToggleTiltToFastForward
        )

        EdgeHairlineDivider()

        FluidMotionToggleRow(
            icon = Icons.Rounded.WavingHand,
            iconTint = Color(0xFF8B5CF6),
            title = "Air Wave (Jedi Swipe)",
            subtitle = "Wave hand over top proximity sensor to $airWaveAction",
            isChecked = isAirWaveEnabled,
            onToggle = onToggleAirWave
        )

        EdgeHairlineDivider()

        FluidMotionToggleRow(
            icon = Icons.Rounded.PhoneAndroid,
            iconTint = Color(0xFFEAB308),
            title = "Pick Up to Play",
            subtitle = "Auto-resume music when lifting phone from table",
            isChecked = isPickUpToPlayEnabled,
            onToggle = onTogglePickUpToPlay
        )

        MadeInIndiaFooter(
            topPadding = 16.dp,
            bottomPadding = 8.dp,
            fontSize = 9.5.sp,
            logoHeight = 12.dp,
            logoWidth = 64.dp,
            watermarkAlpha = 0.45f
        )
        EdgeSettingsBottomSpacer()
    }
}

@Composable
private fun FluidMotionToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
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

        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = sonicletSwitchColors()
        )
    }
}
