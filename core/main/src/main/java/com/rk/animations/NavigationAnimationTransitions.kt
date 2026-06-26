package com.rk.animations

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Shared screen transitions.
 *
 * Uses Material's standard easing curves (incoming content decelerates with [LinearOutSlowInEasing],
 * outgoing content uses [FastOutSlowInEasing]) instead of the default linear interpolation, which
 * removes the "mechanical" feel and keeps spatial continuity between screens. Durations are pulled
 * from [NavigationAnimationValues] so they can be tuned in one place.
 */
object NavigationAnimationTransitions {
    private val enterDuration = NavigationAnimationValues.SlideDuration
    private val exitDuration = (NavigationAnimationValues.SlideDuration * 0.75f).toInt()

    val popEnterTransition =
        fadeIn(tween(enterDuration, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(tween(enterDuration, easing = LinearOutSlowInEasing)) { -it / 3 }

    val popExitTransition =
        fadeOut(tween(exitDuration, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(tween(exitDuration, easing = FastOutSlowInEasing)) { it / 3 }

    val enterTransition =
        fadeIn(tween(enterDuration, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(tween(enterDuration, easing = LinearOutSlowInEasing)) { it / 3 }

    val exitTransition =
        fadeOut(tween(exitDuration, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(tween(exitDuration, easing = FastOutSlowInEasing)) { -it / 3 }
}
