package com.kexora.soniclet.gestures

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.kexora.soniclet.settings.*
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter

/**
 * Ultra-Modern, Fluid & Clean Air & Touch Gestures Settings Screen.
 * 8.dp Universal Side Margin & Transparent Translucent Canvas.
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section 1: Navigation Gestures
        FluidGestureCard(title = "NAVIGATION GESTURES") {
            FluidGestureRow(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                iconTint = Color(0xFF3B82F6),
                title = "1-Finger Edge Swipe",
                subtitle = "Swipe from screen edge to navigate back",
                isChecked = isOneFingerSwipeBackEnabled,
                onToggle = onToggleOneFingerSwipeBack
            )

            FluidGestureDivider()

            FluidGestureRow(
                icon = Icons.Rounded.Home,
                iconTint = Color(0xFF10B981),
                title = "2-Finger Swipe Home",
                subtitle = "Swipe with 2 fingers to jump straight to Library",
                isChecked = isTwoFingerSwipeHomeEnabled,
                onToggle = onToggleTwoFingerSwipeHome
            )

            FluidGestureDivider()

            FluidGestureRow(
                icon = Icons.Rounded.TouchApp,
                iconTint = Color(0xFFF59E0B),
                title = "2-Finger Tap",
                subtitle = "Action: $twoFingerTapAction",
                isChecked = isTwoFingerTapEnabled,
                onToggle = onToggleTwoFingerTap
            )

            FluidGestureDivider()

            FluidGestureRow(
                icon = Icons.Rounded.Swipe,
                iconTint = Color(0xFF8B5CF6),
                title = "3-Finger Swipe",
                subtitle = "Action: $threeFingerSwipeAction",
                isChecked = isThreeFingerSwipeEnabled,
                onToggle = onToggleThreeFingerSwipe
            )
        }

        // Section 2: Player Screen Gestures
        FluidGestureCard(title = "PLAYER SCREEN GESTURES") {
            FluidGestureRow(
                icon = Icons.Rounded.VolumeUp,
                iconTint = Color(0xFF06B6D4),
                title = "Right-Edge Volume Slider",
                subtitle = "Slide finger vertically along right edge to adjust volume",
                isChecked = isVolumeEdgeSwipeEnabled,
                onToggle = onToggleVolumeEdgeSwipe
            )

            FluidGestureDivider()

            FluidGestureRow(
                icon = Icons.Rounded.LinearScale,
                iconTint = Color(0xFFEC4899),
                title = "Artwork Precision Scrubbing",
                subtitle = "Hold album cover and drag horizontally to seek",
                isChecked = isLongPressDragSeekEnabled,
                onToggle = onToggleLongPressDragSeek
            )

            FluidGestureDivider()

            FluidGestureRow(
                icon = Icons.Rounded.SwapVert,
                iconTint = Color(0xFFEAB308),
                title = "MiniPlayer Vertical Swipe",
                subtitle = "Swipe up for Next track, down for Previous",
                isChecked = isMiniPlayerGesturesEnabled,
                onToggle = onToggleMiniPlayerGestures
            )
        }

        // Quick User Guide Link
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenUserGuide() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Interactive Gesture Manual",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Visual demonstrations of all touch & air shortcuts",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
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
private fun FluidGestureCard(
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
private fun FluidGestureDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
        thickness = 0.5.dp
    )
}
