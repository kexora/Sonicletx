package com.kexora.soniclet.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.utils.AudioSpecs

/**
 * Visual badge and pill components representing Audio Codec, Bitrate, and Hi-Res status.
 */

// Premium Hi-Res gold color palette
val HiResGoldLight = Color(0xFFFFD54F)
val HiResGoldDark = Color(0xFFFF8F00)
val HiResGoldBorder = Color(0xFFFFE082)
val HiResBgDark = Color(0xFF1E1700)

// Lossless emerald color palette
val LosslessCyan = Color(0xFF00E5FF)
val LosslessEmerald = Color(0xFF00E676)
val LosslessBgDark = Color(0xFF001F18)

// 320 kbps HQ color palette
val HqPurple = Color(0xFF7C4DFF)
val HqBlue = Color(0xFF448AFF)
val HqBgDark = Color(0xFF120E2E)

// Standard 128 kbps color palette
val StandardSlate = Color(0xFF90A4AE)
val StandardBgDark = Color(0xFF1C2226)

/**
 * Hi-Res Audio Official Style Badge
 */
@Composable
fun HiResAudioBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF221A04), Color(0xFF332608))
                )
            )
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(HiResGoldLight, HiResGoldDark)
                    )
                ),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Hi-Res",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = if (compact) 9.sp else 10.sp,
                    letterSpacing = 0.5.sp
                ),
                color = HiResGoldLight
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "AUDIO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 7.sp else 8.sp,
                    letterSpacing = 1.sp
                ),
                color = HiResGoldBorder
            )
        }
    }
}

/**
 * Compact Pill for track rows or mini player
 */
@Composable
fun CompactAudioBadge(
    specs: AudioSpecs?,
    modifier: Modifier = Modifier
) {
    if (specs == null) return

    val (bgBrush, textColor, borderStroke) = remember(specs) {
        when {
            specs.isHiRes -> Triple(
                Brush.horizontalGradient(listOf(Color(0xFF291E00), Color(0xFF3B2D05))),
                HiResGoldLight,
                BorderStroke(1.dp, HiResGoldDark.copy(alpha = 0.7f))
            )
            specs.isLossless -> Triple(
                Brush.horizontalGradient(listOf(Color(0xFF00221A), Color(0xFF003828))),
                LosslessEmerald,
                BorderStroke(1.dp, LosslessEmerald.copy(alpha = 0.6f))
            )
            specs.format == "MP3" && specs.bitrateKbps >= 300 -> Triple(
                Brush.horizontalGradient(listOf(Color(0xFF151036), Color(0xFF201850))),
                HqBlue,
                BorderStroke(1.dp, HqPurple.copy(alpha = 0.5f))
            )
            specs.format == "MP3" && specs.bitrateKbps in 115..145 -> Triple(
                Brush.horizontalGradient(listOf(Color(0xFF1E2328), Color(0xFF293038))),
                StandardSlate,
                BorderStroke(1.dp, StandardSlate.copy(alpha = 0.35f))
            )
            else -> Triple(
                Brush.horizontalGradient(listOf(Color(0xFF202024), Color(0xFF2B2B30))),
                Color(0xFFCFD8DC),
                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            )
        }
    }

    val labelText = remember(specs) {
        when {
            specs.isHiRes -> "Hi-Res"
            specs.isLossless -> specs.format
            specs.format == "MP3" && specs.bitrateKbps >= 300 -> "320k"
            specs.format == "MP3" && specs.bitrateKbps in 115..145 -> "128k"
            specs.bitrateKbps > 0 -> "${specs.bitrateKbps}k"
            else -> specs.format
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgBrush)
            .border(borderStroke, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.2.sp
            ),
            color = textColor
        )
    }
}

/**
 * Interactive Audio Codec & Bitrate Inspector Bar
 * Placed on the Now Playing screen. Displays:
 * [Hi-Res AUDIO / Format] • [Bitrate: 320 kbps vs 128 kbps] • [Sample Rate: 44.1 kHz / 96 kHz]
 */
@Composable
fun AudioFidelityInspectorBar(
    specs: AudioSpecs?,
    onOpenInspector: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (specs == null) return

    Surface(
        onClick = onOpenInspector,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .testTag("audio_fidelity_inspector_pill"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                specs.isHiRes -> HiResGoldDark.copy(alpha = 0.6f)
                specs.isLossless -> LosslessEmerald.copy(alpha = 0.5f)
                specs.bitrateKbps >= 300 -> HqPurple.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Quality Badge
            if (specs.isHiRes) {
                HiResAudioBadge(compact = true)
                Spacer(modifier = Modifier.width(8.dp))
            } else if (specs.isLossless) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(LosslessBgDark)
                        .border(1.dp, LosslessEmerald.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LOSSLESS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = LosslessEmerald
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Audio Format (FLAC / MP3 / AAC / etc.)
            Text(
                text = specs.format,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = when {
                    specs.isHiRes -> HiResGoldLight
                    specs.isLossless -> LosslessCyan
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            Text(
                text = " • ",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            // Exact Bitrate (e.g. 320 kbps vs 128 kbps)
            Text(
                text = specs.bitrateFormatted,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = when {
                    specs.isHiRes -> HiResGoldLight
                    specs.bitrateKbps >= 300 -> Color(0xFFB388FF)
                    specs.bitrateKbps in 115..145 -> Color(0xFFB0BEC5)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Text(
                text = " • ",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            // Sample Rate (e.g. 44.1 kHz, 48 kHz, 96 kHz, 192 kHz)
            Text(
                text = specs.sampleRateFormatted,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Inspector Trigger Icon
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = "Inspect Audio Codec & Bitrate",
                tint = if (specs.isHiRes) HiResGoldLight.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
