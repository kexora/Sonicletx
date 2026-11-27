package com.kexora.soniclet.ui.components.player

import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kexora.soniclet.player.NavigationTab
import com.kexora.soniclet.player.AppTheme

@Composable
fun Kapar(
    currentTab: NavigationTab,
    trackCount: Int,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    excludedItemCount: Int = 0,
    onOpenExcludedItems: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onOpenSettingsSubPage: ((com.kexora.soniclet.settings.SettingsSubPage) -> Unit)? = null,
    onCreatePlaylist: (() -> Unit)? = null,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    isGridMode: Boolean = false,
    onToggleGridMode: () -> Unit = {},
    appTheme: AppTheme,
    onAppThemeChange: (AppTheme) -> Unit,
    onOpenDrawer: () -> Unit,
    canGoBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    titleText: String? = null,
    suggestionCandidates: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    TopHeaderBar(
        currentTab = currentTab,
        trackCount = trackCount,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        excludedItemCount = excludedItemCount,
        onOpenExcludedItems = onOpenExcludedItems,
        onRefresh = onRefresh,
        onOpenSettingsSubPage = onOpenSettingsSubPage ?: {},
        onCreatePlaylist = onCreatePlaylist,
        isSearchActive = isSearchActive,
        isGridMode = isGridMode,
        onToggleGridMode = onToggleGridMode,
        appTheme = appTheme,
        onAppThemeChange = onAppThemeChange,
        onSearchActiveChange = onSearchActiveChange,
        onOpenDrawer = onOpenDrawer,
        canGoBack = canGoBack,
        onBack = onBack,
        titleText = titleText,
        suggestionCandidates = suggestionCandidates,
        modifier = modifier
    )
}
