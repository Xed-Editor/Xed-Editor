package com.rk.xededitor.ui.animations

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut

object NavigationAnimationTransitions {
    
    val enterTransition: (slideDistance: Int) -> EnterTransition = { slideDistance ->
        materialSharedAxisXIn(
            forward = true,
            slideDistance = slideDistance.takeIf { it > 0 } ?: NavigationAnimationValues.SlideDistance,
            durationMillis = NavigationAnimationValues.SlideDuration,
        )
    }

    val exitTransition: (slideDistance: Int) -> ExitTransition = { slideDistance ->
        materialSharedAxisXOut(
            forward = true,
            slideDistance = slideDistance.takeIf { it > 0 } ?: NavigationAnimationValues.SlideDistance,
            durationMillis = NavigationAnimationValues.SlideDuration,
        )
    }

    val popEnterTransition: (slideDistance: Int) -> EnterTransition = { slideDistance ->
        materialSharedAxisXIn(
            forward = false,
            slideDistance = slideDistance.takeIf { it > 0 } ?: NavigationAnimationValues.SlideDistance,
            durationMillis = NavigationAnimationValues.SlideDuration,
        )
    }

    val popExitTransition: (slideDistance: Int) -> ExitTransition = { slideDistance ->
        materialSharedAxisXOut(
            forward = false,
            slideDistance = slideDistance.takeIf { it > 0 } ?: NavigationAnimationValues.SlideDistance,
            durationMillis = NavigationAnimationValues.SlideDuration,
        )
    }
}