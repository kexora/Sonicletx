package com.kexora.soniclet.settings

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter

/**
 * Streamlined, box-free Folders & Library Settings screen.
 * Everyday English labels, flat edge-to-edge rows, and semantic M3 Light/Dark/Accent tokens.
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
    val context = LocalContext.current
    val totalHiddenCount = excludedFoldersCount + excludedSongsCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("folders_settings_screen")
    ) {
        EdgeSectionHeader(title = "LIBRARY TOOLS")

        // 1. Hidden Songs & Folders
        FolderSettingRow(
            icon = Icons.Rounded.VisibilityOff,
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Hidden Songs & Folders",
            subtitle = "View or unhide songs and folders you hid",
            onClick = onOpenHiddenItems,
            testTag = "folders_hidden_items_row",
            trailing = {
                if (totalHiddenCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "$totalHiddenCount",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        )

        EdgeHairlineDivider(startIndent = 68.dp, endIndent = 16.dp)

        // 2. Call & Voice Recordings
        FolderSettingRow(
            icon = Icons.Rounded.GraphicEq,
            iconTint = MaterialTheme.colorScheme.tertiary,
            title = "Call & Voice Recordings",
            subtitle = "Find call recordings and voice notes on your phone",
            onClick = onOpenSystemHiddenFolders,
            testTag = "folders_call_recordings_row",
            trailing = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "Scan",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        )

        EdgeHairlineDivider(startIndent = 68.dp, endIndent = 16.dp)

        // 3. Remove Duplicate Songs
        FolderSettingRow(
            icon = Icons.Rounded.AutoDelete,
            iconTint = MaterialTheme.colorScheme.secondary,
            title = "Remove Duplicate Songs",
            subtitle = "Find and delete repeated copies of the same song",
            onClick = onOpenDuplicates,
            testTag = "folders_duplicates_row"
        )

        EdgeHairlineDivider(startIndent = 68.dp, endIndent = 16.dp)

        // 4. Refresh Song List
        FolderSettingRow(
            icon = Icons.Rounded.Refresh,
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Refresh Song List",
            subtitle = "Scan your phone for newly added songs",
            onClick = {
                if (!isLoading) {
                    onRefreshMediaStore()
                    Toast.makeText(context, "Scanning your phone for new songs...", Toast.LENGTH_SHORT).show()
                }
            },
            testTag = "folders_refresh_row",
            trailing = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        // Box-Free Short Audio Filter Section
        EdgeSectionHeader(title = "HIDE SHORT CLIPS")

        Text(
            text = "Hide ringtones and short clips under:",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val filterOptions = listOf(
            0L to "Off",
            10_000L to "10s",
            30_000L to "30s",
            60_000L to "60s",
            90_000L to "90s"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterOptions.forEach { (durationValue, label) ->
                val isSelected = minDurationMs == durationValue
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = tween(180),
                    label = "clip_filter_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = tween(180),
                    label = "clip_filter_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .clickable { onSetMinDurationMs(durationValue) }
                        .testTag("folders_filter_${label.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        MadeInIndiaFooter()

        EdgeSettingsBottomSpacer()
    }
}

@Composable
private fun FolderSettingRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}
