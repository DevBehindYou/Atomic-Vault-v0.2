package com.example.ui.theme

import androidx.compose.ui.unit.dp

/** Gaps and padding on the 4 dp grid (design system §5.1). */
object AtomicSpacing {
    /** Optical nudge only (aligning a caption with a neighbour's baseline); not a layout gap. */
    val hairline = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val hero = 56.dp
}

/**
 * Fixed sizes that screens share: icons, marks, tiles, row and bar heights,
 * and content widths. Screen code uses these (or [AtomicSpacing]) instead of
 * raw dp values; the CI design check enforces it.
 */
object AtomicSize {
    val iconSm = 16.dp
    val icon = 20.dp
    val iconLg = 24.dp

    /** Minimum touch target. */
    val touch = 48.dp
    /** Icon tile and compact icon button. */
    val tile = 40.dp
    /** Settings, finding and timeline rows. */
    val row = 56.dp
    /** Top bar and pushed-screen header. */
    val header = 64.dp
    /** Primary button and the FAB pill. */
    val buttonLg = 52.dp

    val markSm = 36.dp
    val markMd = 40.dp
    val markLg = 64.dp

    /** Countdown and strength bars. */
    val bar = 4.dp

    /** Vault list column beside the detail pane on wide screens. */
    val listPane = 360.dp
    /** Unlock and create-vault forms. */
    val formMaxWidth = 480.dp
    /** Reading and generator content. */
    val contentMaxWidth = 560.dp
    /** Settings groups. */
    val wideMaxWidth = 640.dp

    /** Space under the last vault row so the add stack never covers it. */
    val fabClearance = 96.dp
}
