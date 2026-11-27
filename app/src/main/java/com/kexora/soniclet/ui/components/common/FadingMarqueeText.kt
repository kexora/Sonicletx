package com.kexora.soniclet.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun FadingMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    delayMillis: Long = 2000L,
    speedDpPerSecond: Float = 36f,
    enableFade: Boolean = true,
    edgeGradientWidth: Dp = 14.dp
) {
    var textWidth by remember(text) { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    
    BoxWithConstraints(modifier = modifier) {
        val containerWidth = constraints.maxWidth
        val shouldScroll = textWidth > containerWidth
        
        var offsetX by remember(text, shouldScroll) { mutableFloatStateOf(0f) }
        var isScrolling by remember(text, shouldScroll) { mutableStateOf(false) }
        
        LaunchedEffect(textWidth, containerWidth, shouldScroll) {
            if (shouldScroll && containerWidth > 0 && textWidth > 0) {
                val gapPx = 48 * density
                val distance = textWidth.toFloat() + gapPx
                val duration = (distance / (speedDpPerSecond * density)) * 1000f
                
                while (true) {
                    offsetX = 0f
                    isScrolling = false
                    delay(delayMillis)
                    isScrolling = true
                    
                    animate(
                        initialValue = 0f,
                        targetValue = -distance,
                        animationSpec = tween(durationMillis = duration.toInt(), easing = LinearEasing)
                    ) { value, _ ->
                        offsetX = value
                    }
                }
            } else {
                offsetX = 0f
                isScrolling = false
            }
        }
        
        val leftFadeWidthPx = edgeGradientWidth.value * density
        val rightFadeWidthPx = (edgeGradientWidth.value * 1.25f) * density

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { 
                    compositingStrategy = CompositingStrategy.Offscreen
                    clip = true
                }
                .drawWithContent {
                    drawContent()
                    
                    if (isScrolling && enableFade && size.width > 0) {
                        val leftFadeFraction = (leftFadeWidthPx / size.width).coerceIn(0f, 0.35f)
                        val rightFadeFraction = ((size.width - rightFadeWidthPx) / size.width).coerceIn(0.65f, 1f)
                        
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                leftFadeFraction to Color.Black,
                                rightFadeFraction to Color.Black,
                                1.0f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            // First instance of text - starts flush from edge (translationX = offsetX)
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .wrapContentWidth(unbounded = true, align = androidx.compose.ui.Alignment.Start)
                    .graphicsLayer { translationX = offsetX }
                    .onSizeChanged { textWidth = it.width }
            )
            // Second instance for seamless continuous marquee loop from the right side
            if (shouldScroll) {
                val gapPx = 48 * density
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .wrapContentWidth(unbounded = true, align = androidx.compose.ui.Alignment.Start)
                        .graphicsLayer { 
                            translationX = offsetX + textWidth + gapPx 
                        }
                )
            }
        }
    }
}
