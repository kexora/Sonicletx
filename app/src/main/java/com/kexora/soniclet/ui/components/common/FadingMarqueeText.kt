package com.kexora.soniclet.ui.components.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glitch-free single-line marquee text using Compose Foundation's official `basicMarquee`.
 * - Short titles stay cleanly left-aligned and completely motionless with zero edge fade.
 * - Overflowing titles scroll smoothly after 1.5s with a generous 48.dp gap and soft
 *   horizontal edge-fade gradient.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FadingMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    delayMillis: Long = 1500L,
    speedDpPerSecond: Float = 32f,
    enableFade: Boolean = true,
    edgeGradientWidth: Dp = 14.dp
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = constraints.maxWidth
        val measuredWidthPx = remember(text, style, density) {
            textMeasurer.measure(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false
            ).size.width
        }
        val isOverflowing = availableWidthPx > 0 && measuredWidthPx > availableWidthPx
        val fadeWidthPx = with(density) { edgeGradientWidth.toPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isOverflowing && enableFade) {
                        Modifier
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                                clip = true
                            }
                            .drawWithContent {
                                drawContent()
                                if (size.width > 0f) {
                                    val rightFadeStart = ((size.width - fadeWidthPx * 1.2f) / size.width).coerceIn(0.70f, 1f)
                                    val leftFadeEnd = (fadeWidthPx * 0.65f / size.width).coerceIn(0f, 0.20f)
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            0.0f to Color.Transparent,
                                            leftFadeEnd to Color.Black,
                                            rightFadeStart to Color.Black,
                                            1.0f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = if (isOverflowing) {
                    Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = delayMillis.toInt().coerceAtLeast(500),
                            repeatDelayMillis = 2500,
                            spacing = MarqueeSpacing(48.dp),
                            velocity = speedDpPerSecond.dp
                        )
                } else {
                    Modifier.fillMaxWidth()
                }
            )
        }
    }
}
