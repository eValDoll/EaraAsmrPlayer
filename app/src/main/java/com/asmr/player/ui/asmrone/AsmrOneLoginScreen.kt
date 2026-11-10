package com.asmr.player.ui.asmrone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AsmrOneLoginScreen(
    windowSizeClass: WindowSizeClass,
    onDone: () -> Unit,
    viewModel: AsmrOneViewModel = hiltViewModel(),
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = (if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) Modifier.fillMaxSize()
                else Modifier.widthIn(max = 600.dp).fillMaxHeight().fillMaxWidth())
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            AsmrOneAccountContent(viewModel, onLoggedIn = onDone)
        }
    }
}
