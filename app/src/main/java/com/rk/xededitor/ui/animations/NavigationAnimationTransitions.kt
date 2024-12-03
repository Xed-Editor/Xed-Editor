package com.rk.xededitor.ui.animations

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object NavigationAnimationTransitions {
    val popEnterTransition = fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
    val popExitTransition = fadeOut(tween(200)) + slideOutHorizontally { it / 2 }
    val enterTransition = fadeIn(tween(250)) + slideInHorizontally { it / 2 }
    val exitTransition = fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
}