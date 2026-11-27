package com.kexora.soniclet.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter

/**
 * Modern, Fluid & Clean Folders & Library Settings Screen.
 * 8.dp Universal Side Margin & Translucent Canvas.
 */
@Composable
fun AudioFoldersSettingsSubPage(
    excludedFoldersCount: Int,
    excludedSongsCount: Int,
    minDurationMs: Long,
    isLoading: Boolean = false,
    onSetMinDurationMs: (Long) -> Unit,
    onManageFoldersClick: () -> Unit,
    onOpenHiddenItems: () -> Unit,
    onOpenSystemHiddenFolders: () -> Unit = {},
    onRefreshMediaStore: () -> Unit,
    onExcludeJunk: () -> Unit,
    onOpenDuplicates: () -> Unit
) {
    if (isLoading) {
        com.kexora.soniclet.ui.components.common.SkeletonAudioFoldersSettings()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Storage Insights Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Audio Storage Insights",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Local Storage",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Indexed local audio files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Auto-Synced",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Folder Operations Group
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "LIBRARY & STORAGE TOOLS",
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
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    FluidFolderActionRow(
                        title = "Explore Music Folders",
                        subtitle = "Scan and manage your local audio storage directories",
                        icon = Icons.Rounded.FolderOpen,
                        iconTint = Color(0xFFEAB308),
                        onClick = onManageFoldersClick
                    )

                    FluidFolderDivider()

                    FluidFolderActionRow(
                        title = "System Hidden & Recordings Vault",
                        subtitle = "Find Call Recordings, Voice Memos, .nomedia & dot-folders",
                        icon = Icons.Rounded.FolderSpecial,
                        iconTint = Color(0xFF6366F1),
                        badge = "Deep Scan",
                        onClick = onOpenSystemHiddenFolders
                    )

                    FluidFolderDivider()

                    FluidFolderActionRow(
                        title = "Hidden Items Vault",
                        subtitle = if (excludedFoldersCount + excludedSongsCount > 0) "$excludedFoldersCount Folders • $excludedSongsCount Tracks excluded" else "No items currently hidden",
                        icon = Icons.Rounded.VisibilityOff,
                        iconTint = Color(0xFFEC4899),
                        badge = if (excludedFoldersCount + excludedSongsCount > 0) "${excludedFoldersCount + excludedSongsCount}" else null,
                        onClick = onOpenHiddenItems
                    )

                    FluidFolderDivider()

                    FluidFolderActionRow(
                        title = "Duplicate Cleaner",
                        subtitle = "Identify and clean identical duplicate songs",
                        icon = Icons.Rounded.ContentCopy,
                        iconTint = Color(0xFF06B6D4),
                        onClick = onOpenDuplicates
                    )

                    FluidFolderDivider()

                    FluidFolderActionRow(
                        title = "Clean Junk Audio & Voice Notes",
                        subtitle = "Automatically hide WhatsApp notes & notification sounds",
                        icon = Icons.Rounded.CleaningServices,
                        iconTint = Color(0xFF10B981),
                        onClick = onExcludeJunk
                    )

                    FluidFolderDivider()

                    FluidFolderActionRow(
                        title = "Rescan Media Library",
                        subtitle = "Force rescan all storage drives for newly added tracks",
                        icon = Icons.Rounded.Refresh,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onRefreshMediaStore
                    )
                }
            }
        }

        // Short Audio Duration Filter
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "DURATION FILTER",
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
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Hide short audio files (ringtone & voice clips) under duration threshold:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val options = listOf(
                        0L to "Off",
                        10000L to "10s",
                        30000L to "30s",
                        60000L to "60s",
                        90000L to "90s"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { (duration, label) ->
                            FilterChip(
                                selected = minDurationMs == duration,
                                onClick = {
                                    onSetMinDurationMs(duration)
                                    onRefreshMediaStore()
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (minDurationMs == duration) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
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
private fun FluidFolderActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
                .padding(end = 6.dp)
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

        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = iconTint,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun FluidFolderDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
        thickness = 0.5.dp
    )
}
