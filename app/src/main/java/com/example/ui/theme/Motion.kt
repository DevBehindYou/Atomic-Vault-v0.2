package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens (design system §8). Motion confirms an action or reveals
 * content; it never decorates. No springs, bounce or overshoot: CSS `ease`
 * or linear only. When the system animator scale is 0 ("Remove
 * animations"), [LocalReducedMotion] is true and components use short fades
 * only, with no translation or rotation.
 */
object AtomicMotion {
    /** CSS `ease`. */
    val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    const val PRESS_MS = 120
    const val STATE_MS = 150
    const val TOGGLE_MS = 200
    const val ENTER_MS = 350
    const val REVEAL_MS = 500

    fun <T> press(): TweenSpec<T> = tween(PRESS_MS, easing = Ease)
    fun <T> state(): TweenSpec<T> = tween(STATE_MS, easing = Ease)
    fun <T> toggle(): TweenSpec<T> = tween(TOGGLE_MS, easing = Ease)
    fun <T> enter(): TweenSpec<T> = tween(ENTER_MS, easing = Ease)
    fun <T> countdown(durationMs: Int): TweenSpec<T> = tween(durationMs, easing = LinearEasing)

    /** True when the user turned animations off in system settings. */
    fun reducedMotion(context: Context): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) {
        false
    }
}

val LocalReducedMotion = staticCompositionLocalOf { false }
