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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun CustomFadingMarquee(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    delayMillis: Long = 3000L,
    speedDpPerSecond: Float = 30f,
    fadeWidth: Float = 24f // in dp
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val mainPlaceables = subcompose("main") {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false
            )
        }.map { it.measure(constraints.copy(maxWidth = Int.MAX_VALUE)) }

        val textWidth = mainPlaceables.maxOfOrNull { it.width } ?: 0
        val textHeight = mainPlaceables.maxOfOrNull { it.height } ?: 0
        val containerWidth = constraints.maxWidth

        layout(containerWidth, textHeight) {
            // Need a custom drawing modifier? No, we can just return a Box that has the graphics layer.
        }
    }
}
