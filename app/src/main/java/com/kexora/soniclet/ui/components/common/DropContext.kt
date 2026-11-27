package com.kexora.soniclet.ui.components.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
fun DropContext(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.(() -> Unit) -> Unit
) {
    val transitionState = remember { MutableTransitionState(expanded) }
    transitionState.targetState = expanded

    if (transitionState.currentState || transitionState.targetState) {
        Popup(
            alignment = Alignment.TopEnd,
            onDismissRequest = {
                onDismissRequest()
            },
            properties = PopupProperties(focusable = true)
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)) + 
                        slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -30 } + 
                        scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
                exit = fadeOut(tween(140, easing = FastOutLinearInEasing)) + 
                       slideOutVertically(tween(140, easing = FastOutLinearInEasing)) { -30 } + 
                       scaleOut(tween(140, easing = FastOutLinearInEasing), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f))
            ) {
                val configuration = LocalConfiguration.current
                val maxMenuHeight = (configuration.screenHeightDp * 0.8).dp
                
                val primaryColor = MaterialTheme.colorScheme.primary
                val tertiaryColor = MaterialTheme.colorScheme.tertiary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val surfaceBg = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = surfaceBg,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 180.dp, max = (configuration.screenWidthDp * 0.85).dp)
                        .padding(8.dp)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.6f),
                                    tertiaryColor.copy(alpha = 0.5f),
                                    secondaryColor.copy(alpha = 0.4f)
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxMenuHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp, horizontal = 6.dp)
                            .fillMaxWidth()
                    ) {
                        content { 
                            onDismissRequest() 
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DropContextItem(
    text: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    index: Int = 0,
    onClick: () -> Unit
) {
    val isDestructive = iconTint == MaterialTheme.colorScheme.error || text.contains("Delete", ignoreCase = true) || text.contains("Remove", ignoreCase = true)
    
    val badgeBg = if (isDestructive) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    } else {
        iconTint.copy(alpha = 0.16f)
    }

    val badgeTint = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        iconTint
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeTint,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
