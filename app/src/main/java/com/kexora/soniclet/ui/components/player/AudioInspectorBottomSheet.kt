package com.kexora.soniclet.ui.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.utils.AudioSpecs
import com.kexora.soniclet.utils.SmartTitleCleaner


/**
 * Ultra-Compact, Partial-Height Bitrate & Codec Inspector Sheet.
 * Wrap-content height (~45%-52% of screen), opaque frosted obsidian surface, zero card bloat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioInspectorBottomSheet(
    song: Song,
    specs: AudioSpecs?,
    onDismiss: () -> Unit,
    onOpenMetadataEditor: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cleanedTitle = remember(song.title, song.filePath) {
        SmartTitleCleaner.cleanTitle(song.title, song.filePath)
    }
    val cleanedArtist = remember(song.artist) {
        SmartTitleCleaner.cleanArtist(song.artist).ifBlank { "Unknown Artist" }
    }
    val sheetSurface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetSurface,
        contentColor = onSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                        color = hairlineColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 18.dp)
                .testTag("audio_codec_bitrate_inspector_sheet")
        ) {
            // Centered Drag Handle Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
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

            // Compact Top Row: Title + Artist left | Quality Pill + Close right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanedTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanedArtist,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                specs?.let { s ->
                    val (tierLabel, tierColor) = when {
                        s.isHiRes -> "HI-RES" to HiResGoldLight
                        s.isLossless -> "LOSSLESS" to LosslessEmerald
                        s.bitrateKbps >= 300 -> "320K HQ" to MaterialTheme.colorScheme.primary
                        else -> "${s.bitrateKbps}K" to Color(0xFFB0BEC5)
                    }
                    Surface(
                        shape = CircleShape,
                        color = tierColor.copy(alpha = 0.16f),
                        border = BorderStroke(0.7.dp, tierColor.copy(alpha = 0.50f))
                    ) {
                        Text(
                            text = tierLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp
                            ),
                            color = tierColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("audio_inspector_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Inspector",
                        tint = onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (specs == null) {
                com.kexora.soniclet.ui.components.common.SkeletonAudioSpecsInspector()
            } else {
                val accentColor = when {
                    specs.isHiRes -> HiResGoldLight
                    specs.isLossless -> LosslessEmerald
                    else -> MaterialTheme.colorScheme.primary
                }
                val bitrateProgress = remember(specs.bitrateKbps) {
                    when {
                        specs.bitrateKbps <= 96 -> 0.15f
                        specs.bitrateKbps in 97..145 -> 0.30f
                        specs.bitrateKbps in 146..220 -> 0.50f
                        specs.bitrateKbps in 221..280 -> 0.66f
                        specs.bitrateKbps in 281..350 -> 0.82f
                        specs.bitrateKbps in 351..1500 -> 0.94f
                        else -> 1.0f
                    }
                }

                // Sleek Thin Pill-Style Bitrate Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BITRATE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.1.sp
                        ),
                        color = onSurfaceVariant
                    )
                    Text(
                        text = specs.bitrateFormatted,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = accentColor
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { bitrateProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BitrateMarker("128k", specs.bitrateKbps in 97..145, accentColor)
                    BitrateMarker("256k", specs.bitrateKbps in 221..280, accentColor)
                    BitrateMarker("320k", specs.bitrateKbps >= 281 && !specs.isLossless, accentColor)
                    BitrateMarker("Lossless", specs.isLossless || specs.isHiRes, accentColor)
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(thickness = 0.5.dp, color = hairlineColor)

                // Compact Box-Free 2-Column Hairline Key-Value Spec Strip
                CompactSpecRowPair(
                    leftLabel = "Format",
                    leftValue = specs.format.ifBlank { specs.containerExtension.uppercase() },
                    rightLabel = "Sample Rate",
                    rightValue = specs.sampleRateFormatted
                )
                HorizontalDivider(thickness = 0.5.dp, color = hairlineColor)
                CompactSpecRowPair(
                    leftLabel = "Bit Depth",
                    leftValue = specs.bitDepthFormatted,
                    rightLabel = "Channels",
                    rightValue = specs.channelLayout
                )
                HorizontalDivider(thickness = 0.5.dp, color = hairlineColor)
                CompactSpecRowPair(
                    leftLabel = "File Size",
                    leftValue = specs.fileSizeFormatted,
                    rightLabel = "Codec",
                    rightValue = specs.fullCodecName
                )
            }

            if (onOpenMetadataEditor != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onDismiss()
                        onOpenMetadataEditor()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("inspector_open_metadata_editor_btn"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit Tags & Artwork",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun BitrateMarker(
    label: String,
    isActive: Boolean,
    accentColor: Color
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontFeatureSettings = "tnum"
        ),
        color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    )
}

@Composable
private fun CompactSpecRowPair(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = leftValue,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = rightValue,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
