package com.kexora.soniclet.gestures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.kexora.soniclet.settings.*
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter

/**
 * Box-free, edge-to-edge Air & Touch Gestures Settings Screen.
 */
@Composable
fun GestureGuideSubPage(
    onOpenUserGuide: () -> Unit,
    onReplayTour: () -> Unit,
    isTwoFingerTapEnabled: Boolean,
    twoFingerTapAction: String,
    isThreeFingerSwipeEnabled: Boolean,
    threeFingerSwipeAction: String,
    isVolumeEdgeSwipeEnabled: Boolean,
    isLongPressDragSeekEnabled: Boolean,
    onToggleTwoFingerTap: (Boolean) -> Unit,
    onSetTwoFingerTapAction: (String) -> Unit,
    onToggleThreeFingerSwipe: (Boolean) -> Unit,
    onSetThreeFingerSwipeAction: (String) -> Unit,
    onToggleVolumeEdgeSwipe: (Boolean) -> Unit,
    onToggleLongPressDragSeek: (Boolean) -> Unit,
    isOneFingerSwipeBackEnabled: Boolean,
    isTwoFingerSwipeHomeEnabled: Boolean,
    isMiniPlayerGesturesEnabled: Boolean,
    onToggleOneFingerSwipeBack: (Boolean) -> Unit,
    onToggleTwoFingerSwipeHome: (Boolean) -> Unit,
    onToggleMiniPlayerGestures: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Section 1: Navigation Gestures
        EdgeSectionHeader(title = "NAVIGATION GESTURES")

        FluidGestureRow(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            iconTint = Color(0xFF3B82F6),
            title = "1-Finger Edge Swipe",
            subtitle = "Swipe inward from the screen edge to navigate back",
            isChecked = isOneFingerSwipeBackEnabled,
            onToggle = onToggleOneFingerSwipeBack
        )

        EdgeHairlineDivider()

        FluidGestureRow(
            icon = Icons.Rounded.Home,
            iconTint = Color(0xFF10B981),
            title = "2-Finger Swipe Home",
            subtitle = "Swipe with two fingers anywhere to jump straight to Library",
            isChecked = isTwoFingerSwipeHomeEnabled,
            onToggle = onToggleTwoFingerSwipeHome
        )

        EdgeHairlineDivider()

        FluidGestureRow(
            icon = Icons.Rounded.TouchApp,
            iconTint = Color(0xFFF59E0B),
            title = "2-Finger Tap",
            subtitle = "Tap with two fingers simultaneously • Action: $twoFingerTapAction",
            isChecked = isTwoFingerTapEnabled,
            onToggle = onToggleTwoFingerTap
        )

        EdgeHairlineDivider()

        FluidGestureRow(
            icon = Icons.Rounded.Swipe,
            iconTint = Color(0xFF8B5CF6),
            title = "3-Finger Swipe",
            subtitle = "Swipe left or right with three fingers • Action: $threeFingerSwipeAction",
            isChecked = isThreeFingerSwipeEnabled,
            onToggle = onToggleThreeFingerSwipe
        )

        // Section 2: Player Screen Gestures
        EdgeSectionHeader(title = "PLAYER SCREEN GESTURES")

        FluidGestureRow(
            icon = Icons.Rounded.VolumeUp,
            iconTint = Color(0xFF06B6D4),
            title = "Right-Edge Volume Slider",
            subtitle = "Slide vertically along the right edge of Now Playing to adjust volume",
            isChecked = isVolumeEdgeSwipeEnabled,
            onToggle = onToggleVolumeEdgeSwipe
        )

        EdgeHairlineDivider()

        FluidGestureRow(
            icon = Icons.Rounded.LinearScale,
            iconTint = Color(0xFFEC4899),
            title = "Artwork Precision Scrubbing",
            subtitle = "Hold album artwork and drag horizontally for micro-seek",
            isChecked = isLongPressDragSeekEnabled,
            onToggle = onToggleLongPressDragSeek
        )

        EdgeHairlineDivider()

        FluidGestureRow(
            icon = Icons.Rounded.SwapVert,
            iconTint = Color(0xFFEAB308),
            title = "MiniPlayer Vertical Swipe",
            subtitle = "Swipe up on MiniPlayer for Next track, down for Previous",
            isChecked = isMiniPlayerGesturesEnabled,
            onToggle = onToggleMiniPlayerGestures
        )

        // Section 3: Reference Guide
        EdgeSectionHeader(title = "GESTURE REFERENCE")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenUserGuide() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interactive Gesture Manual",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Complete walkthrough of all touch & air shortcuts",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 16.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
private fun FluidGestureRow(
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
