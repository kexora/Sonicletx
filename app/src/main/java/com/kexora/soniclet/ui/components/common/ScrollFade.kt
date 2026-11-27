package com.kexora.soniclet.ui.components.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier

/**
 * Applies a gradient fade at the top of the container only when the list is scrolled.
 * (Currently disabled based on user request)
 */
fun Modifier.scrollFade(listState: LazyListState): Modifier = this

/**
 * Applies a gradient fade at the top of the container only when the grid is scrolled.
 */
fun Modifier.scrollFade(gridState: LazyGridState): Modifier = this

/**
 * Applies a gradient fade at the top of the container only when the scroll view is scrolled.
 * (Currently disabled based on user request)
 */
fun Modifier.scrollFade(scrollState: ScrollState): Modifier = this

