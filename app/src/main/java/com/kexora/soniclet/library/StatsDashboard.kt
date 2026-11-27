package com.kexora.soniclet.library

import androidx.compose.runtime.Composable
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel

/**
 * Entry point wrapper preserved for backward compatibility with navigation routes;
 * delegates directly to the dedicated persistent Room-backed [WrappedScreen].
 */
@Composable
fun StatsDashboard(
    uiState: MusicUiState,
    viewModel: MusicViewModel? = null
) {
    WrappedScreen(
        uiState = uiState,
        viewModel = viewModel
    )
}
