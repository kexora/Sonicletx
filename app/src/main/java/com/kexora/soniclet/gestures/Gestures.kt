package com.kexora.soniclet.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Global App Gestures Container handling:
 * 1. Single finger slide Left-to-Right -> Previous screen (Back).
 * 2. Two finger swipe/touch -> Direct jump to Home (Song Player) or two-finger tap action.
 * 3. Three finger horizontal swipe -> Track / playback control actions.
 */
@Composable
fun GlobalAppGesturesContainer(
    onSingleFingerSwipeRight: () -> Unit,
    onTwoFingerSwipeHome: () -> Unit,
    onTwoFingerTap: () -> Unit,
    onThreeFingerSwipeLeft: () -> Unit,
    onThreeFingerSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(
                onSingleFingerSwipeRight,
                onTwoFingerSwipeHome,
                onTwoFingerTap,
                onThreeFingerSwipeLeft,
                onThreeFingerSwipeRight
            ) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    var maxPointers = 1
                    val startX = firstDown.position.x
                    val startY = firstDown.position.y
                    var lastX = startX
                    var lastY = startY
                    var gestureCanceled = false

                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)

                        if (event.changes.any { it.isConsumed } && maxPointers < 2) {
                            gestureCanceled = true
                            break
                        }

                        val count = event.changes.size
                        if (count > maxPointers) {
                            maxPointers = count
                        }

                        if (event.changes.isNotEmpty()) {
                            val mainChange = event.changes.first()
                            lastX = mainChange.position.x
                            lastY = mainChange.position.y
                        }
                    } while (event.changes.any { it.pressed })

                    if (!gestureCanceled) {
                        val deltaX = lastX - startX
                        val deltaY = lastY - startY
                        val distanceX = abs(deltaX)
                        val distanceY = abs(deltaY)

                        if (maxPointers >= 3) {
                            if (distanceX > 100f && distanceX > distanceY) {
                                if (deltaX > 0) onThreeFingerSwipeRight() else onThreeFingerSwipeLeft()
                            }
                        } else if (maxPointers == 2) {
                            if (distanceX < 50f && distanceY < 50f) {
                                onTwoFingerTap()
                            } else {
                                onTwoFingerSwipeHome()
                            }
                        } else if (maxPointers == 1) {
                            if (startX < 120f && deltaX > 140f && deltaX > distanceY * 2f) {
                                onSingleFingerSwipeRight()
                            }
                        }
                    }
                }
            }
    ) {
        content()
    }
}

/**
 * Container supporting smooth Left-to-Right swipe gesture to return to previous screen.
 */
@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var isEligibleEdgeSwipe by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                accumulatedDrag = 0f
                                isEligibleEdgeSwipe = offset.x <= 96.dp.toPx()
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                accumulatedDrag += dragAmount
                            },
                            onDragEnd = {
                                // Only trigger on genuine Left-to-Right swipe (positive drag) starting near left edge
                                if (isEligibleEdgeSwipe && accumulatedDrag > 72.dp.toPx()) {
                                    onBack()
                                }
                                accumulatedDrag = 0f
                                isEligibleEdgeSwipe = false
                            },
                            onDragCancel = {
                                accumulatedDrag = 0f
                                isEligibleEdgeSwipe = false
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        content()
    }
}
