package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.theme.AtomicTheme

/**
 * Plain page background. The design system keeps texture (the dot grid) for
 * hero areas only, never behind content, so the old calibration grid is
 * gone. Removed in step 7.9 once screens use their own Scaffold colour.
 */
@Composable
fun AmbientVaultBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AtomicTheme.colors.background)
    ) {
        content()
    }
}
