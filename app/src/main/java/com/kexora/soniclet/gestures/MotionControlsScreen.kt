package com.kexora.soniclet.gestures

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.settings.*
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter

/**
 * Modern, Fluid & Clean Hardware Motion & Sensors Settings Screen.
 * 8.dp Universal Side Margin & Translucent Canvas.
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section 1: Dynamic Motion Gestures
        FluidMotionCard(title = "SMART DEVICE SENSORS") {
            // 1. Flip to Pause
            FluidMotionToggleRow(
                icon = Icons.Rounded.ScreenRotation,
                iconTint = Color(0xFFF97316),
                title = "Flip Face-Down",
                subtitle = "Place phone screen down to $flipToPauseAction",
                isChecked = isFlipToPauseEnabled,
                onToggle = onToggleFlipToPause
            )

            FluidMotionDivider()

            // 2. Wrist Twist
            FluidMotionToggleRow(
                icon = Icons.Rounded.Watch,
                iconTint = Color(0xFF3B82F6),
                title = "Wrist Twist",
                subtitle = "Twist wrist back and forth to $wristTwistAction",
                isChecked = isWristTwistEnabled,
                onToggle = onToggleWristTwist
            )

            FluidMotionDivider()

            // 3. Pocket Mode
            FluidMotionToggleRow(
                icon = Icons.Rounded.Security,
                iconTint = Color(0xFF10B981),
                title = "Smart Pocket Mode",
                subtitle = "Auto-pause & lock controls when phone is in pocket",
                isChecked = isPocketModeEnabled,
                onToggle = onTogglePocketMode
            )

            FluidMotionDivider()

            // 4. Hard Shake
            FluidMotionToggleRow(
                icon = Icons.Rounded.Vibration,
                iconTint = Color(0xFFEC4899),
                title = "Hard Shake",
                subtitle = "Firm shake to $shakeAction",
                isChecked = isShakeEnabled,
                onToggle = onToggleShakeEnabled
            )

            FluidMotionDivider()

            // 5. Tilt to Fast-Forward
            FluidMotionToggleRow(
                icon = Icons.Rounded.CompareArrows,
                iconTint = Color(0xFF06B6D4),
                title = "Tilt to Fast-Forward",
                subtitle = "Tilt phone left/right in player to scrub track",
                isChecked = isTiltToFastForwardEnabled,
                onToggle = onToggleTiltToFastForward
            )

            FluidMotionDivider()

            // 6. Air Wave Proximity Gesture
            FluidMotionToggleRow(
                icon = Icons.Rounded.WavingHand,
                iconTint = Color(0xFF8B5CF6),
                title = "Air Wave (Jedi Swipe)",
                subtitle = "Wave hand over top proximity sensor to $airWaveAction",
                isChecked = isAirWaveEnabled,
                onToggle = onToggleAirWave
            )

            FluidMotionDivider()

            // 7. Pick Up to Play
            FluidMotionToggleRow(
                icon = Icons.Rounded.PhoneAndroid,
                iconTint = Color(0xFFEAB308),
                title = "Pick Up to Play",
                subtitle = "Auto-resume music when lifting phone from table",
                isChecked = isPickUpToPlayEnabled,
                onToggle = onTogglePickUpToPlay
            )
        }

        // Info Banner
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.BatteryChargingFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Sensors run in low-power mode and activate only while music is playing.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        MadeInIndiaFooter(
            topPadding = 12.dp,
            bottomPadding = 8.dp,
            fontSize = 9.5.sp,
            logoHeight = 12.dp,
            logoWidth = 64.dp,
            watermarkAlpha = 0.45f
        )
        Spacer(modifier = Modifier.height(com.kexora.soniclet.LocalPlayerBottomPadding.current + 8.dp))
    }
}

@Composable
private fun FluidMotionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.padding(start = 4.dp, bottom = 5.dp)
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                content = content
            )
        }
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
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
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.5.sp,
                    lineHeight = 12.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun FluidMotionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
        thickness = 0.5.dp
    )
}
