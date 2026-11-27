package com.kexora.soniclet.ui.components.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Toast badge indicator representation.
 * By default, uses the official Soniclet App Vector Logo.
 */
sealed class ToastBadge {
    data object AppLogo : ToastBadge()
    data class Action(val icon: ImageVector) : ToastBadge()
}

/**
 * Premium Floating Toast Pill Notification Banner
 *
 * Features:
 * - Fluid spring-driven slide & scale in/out animations
 * - Interactive vertical swipe-up tracking to flick away
 * - Context-aware badge with the official Soniclet vector logo by default
 * - Obsidian/frosted glassmorphic pill with glowing ambient shadow and hairline gradient border
 * - Zero vertical stretching with clean typography and smooth text transitions
 */
@Composable
fun AnimatedToastBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 2500L
) {
    var activeMessage by remember { mutableStateOf<String?>(null) }
    var isVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }

    fun dismissToast() {
        scope.launch {
            isVisible = false
            dragOffsetY.animateTo(80f, tween(180, easing = FastOutLinearInEasing))
            delay(180)
            activeMessage = null
            dragOffsetY.snapTo(0f)
            onDismiss()
        }
    }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            activeMessage = message
            isVisible = true
            dragOffsetY.snapTo(0f)
            delay(durationMs)
            isVisible = false
            delay(220)
            activeMessage = null
            onDismiss()
        } else if (isVisible) {
            isVisible = false
            delay(220)
            activeMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isVisible && !activeMessage.isNullOrBlank(),
            enter = slideInVertically(
                initialOffsetY = { it + 60 },
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(
                animationSpec = tween(220, easing = LinearOutSlowInEasing)
            ) + scaleIn(
                initialScale = 0.86f,
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { it + 60 },
                animationSpec = tween(200, easing = FastOutLinearInEasing)
            ) + fadeOut(
                animationSpec = tween(160)
            ) + scaleOut(
                targetScale = 0.88f,
                animationSpec = tween(160)
            ),
            modifier = Modifier.wrapContentSize(Alignment.BottomCenter)
        ) {
            val currentMsg = activeMessage
            if (!currentMsg.isNullOrBlank()) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val tertiaryColor = MaterialTheme.colorScheme.tertiary
                val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest

                // Context-aware badge: defaults to the official app vector logo
                val badge: ToastBadge = remember(currentMsg) {
                    val lower = currentMsg.lowercase()
                    when {
                        lower.contains("favorite") || lower.contains("heart") || lower.contains("liked") ->
                            ToastBadge.Action(Icons.Rounded.Favorite)
                        lower.contains("delete") || lower.contains("remove") ->
                            ToastBadge.Action(Icons.Rounded.DeleteOutline)
                        lower.contains("hide") || lower.contains("hidden") ->
                            ToastBadge.Action(Icons.Rounded.VisibilityOff)
                        lower.contains("copied") || lower.contains("copy") ->
                            ToastBadge.Action(Icons.Rounded.Check)
                        // By default, instead of generic icon, display the official Soniclet App Vector Logo!
                        else -> ToastBadge.AppLogo
                    }
                }

                Surface(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .widthIn(min = 160.dp, max = 380.dp)
                        .heightIn(min = 46.dp, max = 56.dp)
                        .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                        .shadow(
                            elevation = 14.dp,
                            shape = CircleShape,
                            spotColor = primaryColor.copy(alpha = 0.45f),
                            ambientColor = Color.Black.copy(alpha = 0.60f)
                        )
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.85f),
                                    tertiaryColor.copy(alpha = 0.75f),
                                    primaryColor.copy(alpha = 0.85f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetY.value > 18f) {
                                        dismissToast()
                                    } else {
                                        scope.launch {
                                            dragOffsetY.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount > 0f || dragOffsetY.value > 0f) {
                                        scope.launch {
                                            dragOffsetY.snapTo(
                                                (dragOffsetY.value + dragAmount)
                                                    .coerceAtLeast(-8f)
                                                    .coerceAtMost(100f)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            dismissToast()
                        },
                    shape = CircleShape,
                    color = surfaceColor.copy(alpha = 0.96f),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        primaryColor.copy(alpha = 0.16f),
                                        tertiaryColor.copy(alpha = 0.12f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Glowing Emblem Badge with the Official Soniclet App Vector Logo by Default
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(primaryColor, tertiaryColor)
                                    )
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = Color.White.copy(alpha = 0.35f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (badge) {
                                is ToastBadge.AppLogo -> {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_soniclet_app_logo),
                                        contentDescription = "Soniclet Logo",
                                        modifier = Modifier.size(17.dp),
                                        colorFilter = ColorFilter.tint(Color.White)
                                    )
                                }
                                is ToastBadge.Action -> {
                                    Icon(
                                        imageVector = badge.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Smoothly animated Message Text
                        AnimatedContent(
                            targetState = currentMsg,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn(tween(140)))
                                    .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(110)))
                            },
                            modifier = Modifier.weight(1f, fill = false),
                            label = "ToastTextTransition"
                        ) { text ->
                            Text(
                                text = text,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Sleek Micro Dismiss Icon
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    dismissToast()
                                }
                        )
                    }
                }
            }
        }
    }
}
