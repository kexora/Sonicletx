package com.kexora.soniclet.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter

enum class ManualSection { ALL, GESTURES, MOTION }

/**
 * Box-free, edge-to-edge User Manual & Feature Guide.
 */
@Composable
fun UserManualSubPage(section: ManualSection) {
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = remember {
        listOf(
            "ALL" to "All Features",
            "AUDIO" to "Sound Engine",
            "GESTURES" to "Touch Gestures",
            "MOTION" to "Motion Sensors",
            "TOOLS" to "Smart Tools"
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Quick Category Filter Pills (true edge-to-edge LazyRow)
        if (section == ManualSection.ALL) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categories) { (key, label) ->
                    val isSelected = selectedCategory == key
                    Surface(
                        onClick = { selectedCategory = key },
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = if (isSelected) null else BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Section: Audio Engine & Sound Architecture
        if (section == ManualSection.ALL && (selectedCategory == "ALL" || selectedCategory == "AUDIO")) {
            EdgeSectionHeader(title = "AUDIO ENGINE & HIGH-RES SOUND")

            ManualItemRow(
                icon = Icons.Rounded.AllInclusive,
                iconTint = Color(0xFF00E676),
                title = "Studio Gapless Playback",
                description = "Eliminates silence between consecutive tracks for seamless concert recordings, DJ sets, and symphony albums."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.Tune,
                iconTint = Color(0xFF00E5FF),
                title = "DJ Track Crossfade",
                description = "Smoothly overlaps the ending track into the incoming song without abrupt cuts (customizable from 1s to 12s)."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.SpatialAudioOff,
                iconTint = Color(0xFFAA00FF),
                title = "Multi-App Playback & Audio Ducking",
                description = "Play music together with YouTube, games, or GPS directions. Music volume softly dips during navigation voice alerts and automatically restores."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.GraphicEq,
                iconTint = Color(0xFFFF9100),
                title = "8D Spatial Audio & Equalizer",
                description = "Hardware DSP equalizer with 10+ audiophile presets, virtualizer 3D soundstage, and dynamic 8D audio stereo rotation."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.Vibration,
                iconTint = Color(0xFFFF4081),
                title = "Haptic Bass Vibration",
                description = "Synchronizes your device's precision haptic motor with low-frequency sub-bass beats for a physical club-like experience."
            )
        }

        // Section: Touch & Multi-Finger Gestures
        if (section == ManualSection.GESTURES || (section == ManualSection.ALL && (selectedCategory == "ALL" || selectedCategory == "GESTURES"))) {
            EdgeSectionHeader(title = "TOUCH & MULTI-FINGER GESTURES")

            ManualItemRow(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                iconTint = Color(0xFFE53935),
                title = "1-Finger Edge Swipe",
                description = "Swipe inward from the left screen edge to go back to the previous screen or smoothly dismiss subpages."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.Home,
                iconTint = Color(0xFF8E24AA),
                title = "2-Finger Swipe (Up/Down)",
                description = "Swipe with two fingers anywhere on any screen to instantly jump straight back to your Home Music Library."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.TouchApp,
                iconTint = Color(0xFF00ACC1),
                title = "2-Finger Tap",
                description = "Tap anywhere on the screen with two fingers simultaneously to toggle instant Play / Pause."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.Swipe,
                iconTint = Color(0xFFF4511E),
                title = "3-Finger Swipe (Left / Right)",
                description = "Swipe left or right with three fingers to skip to the Next or Previous track from anywhere in the app."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.VolumeUp,
                iconTint = Color(0xFF3949AB),
                title = "Right-Edge Volume Slider",
                description = "Slide vertically along the far-right edge of the full-screen player to adjust volume smoothly."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.LinearScale,
                iconTint = Color(0xFF00897B),
                title = "Long Press & Drag Seek",
                description = "Hold your finger on the album artwork and drag left or right for micro-precision audio scrubbing."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.SwapVert,
                iconTint = Color(0xFFD81B60),
                title = "MiniPlayer Quick Swipe",
                description = "Swipe up on the bottom mini-player to play Next Track; swipe down to return to Previous Track."
            )
        }

        // Section: Motion & Sensor Automations
        if (section == ManualSection.MOTION || (section == ManualSection.ALL && (selectedCategory == "ALL" || selectedCategory == "MOTION"))) {
            EdgeSectionHeader(title = "MOTION & SENSOR AUTOMATIONS")

            ManualItemRow(
                icon = Icons.Rounded.ScreenRotation,
                iconTint = Color(0xFFFF8F00),
                title = "Shake Phone to Skip / Shuffle",
                description = "Give your phone a brisk shake to skip to the next track or randomize your currently active queue."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.PhoneAndroid,
                iconTint = Color(0xFF8D6E63),
                title = "Flip Face-Down (Instant Mute)",
                description = "Turn your phone face-down on a desk or bed to instantly mute or pause playback without touching buttons."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.Autorenew,
                iconTint = Color(0xFF039BE5),
                title = "Wrist Twist Gesture",
                description = "Twist your wrist twice quickly to perform custom actions like adding the track to Favorites or skipping."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.PanTool,
                iconTint = Color(0xFFF06292),
                title = "Air Wave (Jedi Proximity Swipe)",
                description = "Wave your hand over the top proximity sensor to Play, Pause, or Skip hands-free while cooking, driving, or working out."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.Smartphone,
                iconTint = Color(0xFF78909C),
                title = "Smart Pocket Mode",
                description = "Uses proximity and ambient sensors to automatically pause when placed in your pocket, and resumes when taken out."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.ArrowUpward,
                iconTint = Color(0xFF4DD0E1),
                title = "Pick Up to Play",
                description = "Lifting your phone from a flat surface automatically wakes the playback engine and resumes your music."
            )
        }

        // Section: Smart Tools & Library Features
        if (section == ManualSection.ALL && (selectedCategory == "ALL" || selectedCategory == "TOOLS")) {
            EdgeSectionHeader(title = "SMART TOOLS & LIBRARY FEATURES")

            ManualItemRow(
                icon = Icons.Rounded.ContentCopy,
                iconTint = Color(0xFF00E676),
                title = "Intelligent Duplicate Cleaner",
                description = "Multi-pass algorithm analyzes titles, audio bitrate, and tags to detect redundant tracks and reclaim valuable device storage."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.FolderOff,
                iconTint = Color(0xFF00E5FF),
                title = "Hidden Folders & Private Vault",
                description = "Hide WhatsApp voice notes, recordings, ringtones, or specific sensitive folders from the main music queue and shuffle."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.VideoLibrary,
                iconTint = Color(0xFFAA00FF),
                title = "Video to M4A Audio Extractor",
                description = "Extract studio-quality audio from any local video file (MP4, MKV, WebM) and add it directly to your music library."
            )
            EdgeHairlineDivider()
            ManualItemRow(
                icon = Icons.Rounded.Bedtime,
                iconTint = Color(0xFFFF9100),
                title = "Smart Sleep Timer with Fadeout",
                description = "Set auto-pause timers with smooth audio fadeout so you can fall asleep peacefully without sudden silence."
            )
        }

        MadeInIndiaFooter()
        EdgeSettingsBottomSpacer()
    }
}

@Composable
private fun ManualItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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

        Column(modifier = Modifier.weight(1f)) {
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
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
