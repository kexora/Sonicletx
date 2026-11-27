package com.kexora.soniclet.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.utils.AudioSpecs

/**
 * Interactive Audio Codec & Bitrate Inspector Bottom Sheet.
 * Provides deep audio fidelity diagnostics:
 * - High-Res Audio Badge & Certification criteria
 * - FLAC / MP3 / AAC / WAV Format verification
 * - Exact Bitrate (320kbps vs 128kbps) with visual quality spectrum meter
 * - Exact Sample Rate (44.1kHz, 48kHz, 96kHz, 192kHz) and Bit Depth
 * - Channel layout & container storage details
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState())
                .testTag("audio_codec_bitrate_inspector_sheet")
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Audio Codec & Bitrate Inspector",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hardware audio stream inspection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("audio_inspector_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Inspector",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (specs == null) {
                com.kexora.soniclet.ui.components.common.SkeletonAudioSpecsInspector()
            }

            // HERO CARD: Format, Codec, Hi-Res Badge
            specs?.let { s ->
                val heroBorderColor = when {
                    s.isHiRes -> HiResGoldDark.copy(alpha = 0.7f)
                    s.isLossless -> LosslessEmerald.copy(alpha = 0.6f)
                    s.bitrateKbps >= 300 -> HqPurple.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }

                val heroBgBrush = when {
                    s.isHiRes -> Brush.verticalGradient(listOf(Color(0xFF2E2205), Color(0xFF191301)))
                    s.isLossless -> Brush.verticalGradient(listOf(Color(0xFF02261E), Color(0xFF00130E)))
                    s.bitrateKbps >= 300 -> Brush.verticalGradient(listOf(Color(0xFF1D143D), Color(0xFF0D0820)))
                    else -> Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.5.dp, heroBorderColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(heroBgBrush)
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                if (s.isHiRes) {
                                    HiResAudioBadge()
                                } else if (s.isLossless) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(LosslessBgDark)
                                            .border(1.dp, LosslessEmerald, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "LOSSLESS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            ),
                                            color = LosslessEmerald
                                        )
                                    }
                                } else if (s.format == "MP3" && s.bitrateKbps >= 300) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(HqBgDark)
                                            .border(1.dp, HqPurple, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "320K HQ",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            ),
                                            color = Color(0xFFB388FF)
                                        )
                                    }
                                } else if (s.format == "MP3" && s.bitrateKbps in 115..145) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(StandardBgDark)
                                            .border(1.dp, StandardSlate.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "128 KBPS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = StandardSlate
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Codec & Format line
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (s.isLossless) Icons.Rounded.Verified else Icons.Rounded.Equalizer,
                                    contentDescription = null,
                                    tint = if (s.isHiRes) HiResGoldLight else if (s.isLossless) LosslessEmerald else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = s.fullCodecName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BITRATE DEEP-DIVE CARD (320kbps vs 128kbps vs Lossless)
            specs?.let { s ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "BITRATE INSPECTOR",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = s.bitrateFormatted,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp
                                ),
                                color = when {
                                    s.isHiRes -> HiResGoldLight
                                    s.bitrateKbps >= 300 -> Color(0xFFB388FF)
                                    s.bitrateKbps in 115..145 -> Color(0xFFB0BEC5)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bitrate Quality Comparison Spectrum Bar
                        // Target points: 96k (low) -> 128k (std) -> 256k (high) -> 320k (studio) -> 1411k (lossless) -> 4000k+ (hi-res)
                        val progress = remember(s.bitrateKbps) {
                            when {
                                s.bitrateKbps <= 96 -> 0.15f
                                s.bitrateKbps in 97..145 -> 0.30f // 128k range
                                s.bitrateKbps in 146..220 -> 0.50f // 192k range
                                s.bitrateKbps in 221..280 -> 0.65f // 256k range
                                s.bitrateKbps in 281..350 -> 0.80f // 320k range (Max MP3 CBR)
                                s.bitrateKbps in 351..1500 -> 0.92f // 1411k CD Lossless
                                else -> 1.0f // Hi-Res 24-bit/96kHz
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                s.isHiRes -> HiResGoldLight
                                s.isLossless -> LosslessEmerald
                                s.bitrateKbps >= 300 -> HqPurple
                                s.bitrateKbps in 115..145 -> StandardSlate
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Labels below spectrum
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "128k (Standard)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (s.bitrateKbps in 115..145) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                ),
                                color = if (s.bitrateKbps in 115..145) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "256k (High)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (s.bitrateKbps in 240..270) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                ),
                                color = if (s.bitrateKbps in 240..270) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "320k (Studio HQ)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (s.bitrateKbps >= 300 && !s.isLossless) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                ),
                                color = if (s.bitrateKbps >= 300 && !s.isLossless) Color(0xFFB388FF) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "1411k (Lossless)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (s.isLossless) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                ),
                                color = if (s.isLossless) LosslessEmerald else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Explanation Description
                        val explanation = remember(s) {
                            when {
                                s.isHiRes -> "Master Audio Fidelity: 24-bit ultra-wide dynamic range with high sampling rate exceeding CD Red Book standards."
                                s.isLossless -> "Bit-Perfect Lossless: Exact master copy without any psychoacoustic compression artifacts (1411 kbps PCM equivalent)."
                                s.bitrateKbps >= 300 -> "Maximum MP3 Fidelity (320 kbps CBR): Highest possible MP3 bitrate. Preserves maximum acoustic detail with negligible high-frequency rolloff."
                                s.bitrateKbps in 115..145 -> "Standard Audio Quality (128 kbps): Compact compression format optimized for reduced storage space and basic streaming."
                                else -> "Compressed Audio Stream: Encoded at ${s.bitrateKbps} kbps data throughput."
                            }
                        }

                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SAMPLE RATE & RESOLUTION GRID
            specs?.let { s ->
                Text(
                    text = "SAMPLING & RESOLUTION",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SpecCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Waves,
                        label = "Sample Rate",
                        value = s.sampleRateFormatted,
                        subValue = "${s.sampleRateHz} Hz",
                        isHighlight = s.sampleRateHz >= 48000
                    )
                    SpecCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Memory,
                        label = "Bit Depth",
                        value = s.bitDepthFormatted,
                        subValue = if (s.bitDepth >= 24) "Studio Master" else "CD Standard",
                        isHighlight = s.bitDepth >= 24
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SpecCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.SurroundSound,
                        label = "Channels",
                        value = s.channelLayout,
                        subValue = if (s.channels == 2) "Stereo Separation" else "${s.channels}ch Audio",
                        isHighlight = false
                    )
                    SpecCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Storage,
                        label = "File Size",
                        value = s.fileSizeFormatted,
                        subValue = ".${s.containerExtension.uppercase()}",
                        isHighlight = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Button: Open Song Details / Tag Editor
            if (onOpenMetadataEditor != null) {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenMetadataEditor()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("inspector_open_metadata_editor_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit Tags & Album Artwork",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SpecCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    subValue: String,
    isHighlight: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlight) {
                HiResGoldDark.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = if (isHighlight) BorderStroke(1.dp, HiResGoldDark.copy(alpha = 0.4f)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHighlight) HiResGoldLight else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isHighlight) HiResGoldLight else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}
