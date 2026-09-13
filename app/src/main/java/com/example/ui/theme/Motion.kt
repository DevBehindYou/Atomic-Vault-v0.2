package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens for Liquid Glass. LiquidAccessories specifies one curve
 * for everything: cubic-bezier(.22, 1, .36, 1). GlassEasing is that curve
 * for programmatic/eased transitions; GlassSpring is for anything the user
 * is actively touching or dragging, where a physical spring reads better
 * than a fixed-duration ease (see the design plan's Interaction &
 * Animation Spec). Durations are kept short throughout -- "trust before
 * beauty" in the design plan means nothing here should make someone wait
 * to find out whether an unlock succeeded.
 */
object AtomicMotion {
    val GlassEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    val GlassSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    const val DURATION_FAST_MS = 120 // press feedback, toggle flip
    const val DURATION_BASE_MS = 220 // card expand, sheet in/out
    const val DURATION_SLOW_MS = 360 // screen-level transitions only

    val fastTween = tween<Float>(durationMillis = DURATION_FAST_MS, easing = GlassEasing)
    val baseTween = tween<Float>(durationMillis = DURATION_BASE_MS, easing = GlassEasing)
    val slowTween = tween<Float>(durationMillis = DURATION_SLOW_MS, easing = GlassEasing)
}
