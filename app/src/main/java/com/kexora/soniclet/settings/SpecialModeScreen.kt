package com.kexora.soniclet.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.easteregg.MuhLoveRegistry
import com.kexora.soniclet.easteregg.SpecialDateEntry
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.splash.SonicletSplashTier
import java.util.UUID

private val MonthLabels = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * Clean, box-free edge-to-edge Special Mode & Special Dates Management Screen.
 * Manages:
 *  1. isSpecialModeEnabled: Boolean
 *  2. specialDates: List<SpecialDateEntry> (add / edit / delete / toggle)
 *  3. One-tap "Preview Splash" triggers for Special Mode Splash (Tier 2) and Special Date Celebration Splash (Tier 3).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecialModeSettingsSubPage(
    isSpecialModeEnabled: Boolean,
    specialDates: List<SpecialDateEntry>,
    onToggleSpecialMode: (Boolean) -> Unit,
    onUpdateSpecialDates: (List<SpecialDateEntry>) -> Unit,
    onPreviewSplash: (SonicletSplashTier, SpecialDateEntry?) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<SpecialDateEntry?>(null) }

    val todayMatch = remember(specialDates) {
        MuhLoveRegistry.getTodayMatchedSpecialDateEntry(specialDates)
    }

    if (showEditorDialog) {
        SpecialDateEditorDialog(
            initialEntry = editingEntry,
            onDismiss = {
                showEditorDialog = false
                editingEntry = null
            },
            onSave = { saved ->
                val updated = if (editingEntry != null) {
                    specialDates.map { if (it.id == saved.id) saved else it }
                } else {
                    specialDates + saved
                }
                onUpdateSpecialDates(updated)
                showEditorDialog = false
                editingEntry = null
                Toast.makeText(context, "Special date saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .testTag("special_mode_settings_screen")
    ) {
        // =====================================================
        // 1. MASTER SPECIAL MODE TOGGLE ROW
        // =====================================================
        EdgeSectionHeader(title = "SPECIAL EDITION MODE")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleSpecialMode(!isSpecialModeEnabled) }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("special_mode_master_toggle"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Special Mode",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (isSpecialModeEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = if (isSpecialModeEnabled) "ACTIVE" else "OFF",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isSpecialModeEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Enables the Special Edition splash screen and automatic celebration greetings on your saved special dates",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = isSpecialModeEnabled,
                onCheckedChange = { onToggleSpecialMode(it) }
            )
        }

        if (todayMatch != null) {
            EdgeInfoBanner(
                icon = Icons.Rounded.Celebration,
                title = "Today matches ${todayMatch.formattedShortDate()}!",
                message = "${todayMatch.title} — ${todayMatch.message}"
            )
        }

        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        // =====================================================
        // 2. ONE-TAP SPLASH SCREEN PREVIEWS
        // =====================================================
        EdgeSectionHeader(title = "PREVIEW SPLASH SCREENS")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Test how Soniclet's animated launch screen looks in each mode:",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { onPreviewSplash(SonicletSplashTier.TIER_2_SPECIAL_MODE, null) },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("preview_special_mode_splash_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Preview Special Splash",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        val previewEntry = todayMatch
                            ?: specialDates.firstOrNull { it.isEnabled }
                            ?: specialDates.firstOrNull()
                        onPreviewSplash(SonicletSplashTier.TIER_3_SPECIAL_DATE, previewEntry)
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("preview_special_date_splash_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Celebration,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Preview Date Splash",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        // =====================================================
        // 3. SAVED SPECIAL DATES LIST (ADD / EDIT / DELETE / TOGGLE)
        // =====================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SPECIAL DATES (${specialDates.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.0.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(
                    onClick = {
                        val defaults = MuhLoveRegistry.getDefaultSpecialDateEntries()
                        onUpdateSpecialDates(defaults)
                        Toast.makeText(context, "Default special dates restored", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = "Reset default dates",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Defaults",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                FilledTonalButton(
                    onClick = {
                        editingEntry = null
                        showEditorDialog = true
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("add_special_date_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add Special Date",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Date",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }
        }

        if (specialDates.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No special dates saved yet",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add birthdays or anniversaries to see the Special Date Celebration Splash on those days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            specialDates.forEachIndexed { index, entry ->
                SpecialDateEdgeRow(
                    entry = entry,
                    onToggleEnabled = { enabled ->
                        val updated = specialDates.map {
                            if (it.id == entry.id) it.copy(isEnabled = enabled) else it
                        }
                        onUpdateSpecialDates(updated)
                    },
                    onPreview = {
                        onPreviewSplash(SonicletSplashTier.TIER_3_SPECIAL_DATE, entry)
                    },
                    onEdit = {
                        editingEntry = entry
                        showEditorDialog = true
                    },
                    onDelete = {
                        val updated = specialDates.filterNot { it.id == entry.id }
                        onUpdateSpecialDates(updated)
                        Toast.makeText(context, "Special date removed", Toast.LENGTH_SHORT).show()
                    }
                )
                if (index < specialDates.lastIndex) {
                    EdgeHairlineDivider(startIndent = 72.dp, endIndent = 16.dp)
                }
            }
        }

        MadeInIndiaFooter()
        EdgeSettingsBottomSpacer()
    }
}

@Composable
private fun SpecialDateEdgeRow(
    entry: SpecialDateEntry,
    onToggleEnabled: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("special_date_row_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date pill badge
        Surface(
            shape = CircleShape,
            color = if (entry.isEnabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.size(46.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = entry.day.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    ),
                    color = if (entry.isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                val monthIdx = (entry.month - 1).coerceIn(0, 11)
                Text(
                    text = MonthLabels[monthIdx],
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.5.sp
                    ),
                    color = if (entry.isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp
                ),
                color = if (entry.isEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action icons: Preview Splash, Edit, Delete, Toggle
        IconButton(
            onClick = onPreview,
            modifier = Modifier
                .size(34.dp)
                .testTag("preview_date_${entry.id}")
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Preview Splash for ${entry.title}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }

        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Edit ${entry.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete ${entry.title}",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        Switch(
            checked = entry.isEnabled,
            onCheckedChange = onToggleEnabled
        )
    }
}

@Composable
private fun SpecialDateEditorDialog(
    initialEntry: SpecialDateEntry?,
    onDismiss: () -> Unit,
    onSave: (SpecialDateEntry) -> Unit
) {
    var title by remember(initialEntry) { mutableStateOf(initialEntry?.title ?: "") }
    var message by remember(initialEntry) { mutableStateOf(initialEntry?.message ?: "") }
    var selectedMonth by remember(initialEntry) { mutableIntStateOf(initialEntry?.month ?: 10) }
    var selectedDay by remember(initialEntry) { mutableIntStateOf(initialEntry?.day ?: 22) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialEntry == null) "Add Special Date" else "Edit Special Date",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Headline (e.g., Happy Birthday ✨)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Warm Greeting Message") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Select Month",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(MonthLabels) { idx, label ->
                        val monthNum = idx + 1
                        val isSelected = selectedMonth == monthNum
                        Surface(
                            onClick = { selectedMonth = monthNum },
                            shape = CircleShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Select Day ($selectedDay ${MonthLabels[(selectedMonth - 1).coerceIn(0, 11)]})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(31) { dayIdx ->
                        val dayNum = dayIdx + 1
                        val isSelected = selectedDay == dayNum
                        Surface(
                            onClick = { selectedDay = dayNum },
                            shape = CircleShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ) {
                            Box(
                                modifier = Modifier.size(34.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifBlank { "Happy Special Day ✨" }
                    val finalMessage = message.trim().ifBlank { "Wishing you a joyful day filled with music 💖" }
                    onSave(
                        SpecialDateEntry(
                            id = initialEntry?.id ?: "special_${UUID.randomUUID()}",
                            title = finalTitle,
                            message = finalMessage,
                            day = selectedDay.coerceIn(1, 31),
                            month = selectedMonth.coerceIn(1, 12),
                            isEnabled = initialEntry?.isEnabled ?: true
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
