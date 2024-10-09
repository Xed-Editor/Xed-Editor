package com.rk.xededitor.ui.animations

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

import soup.compose.material.motion.animation.materialSharedAxisYIn
import soup.compose.material.motion.animation.materialSharedAxisYOut

object NavigationAnimationTransitions {

    val enterTransition: () -> EnterTransition = {
        materialSharedAxisYIn(forward = true, slideDistance = NavigationAnimationValues.SlideDistance, durationMillis = NavigationAnimationValues.SlideDuration)
    }

    val exitTransition: () -> ExitTransition = {
        materialSharedAxisYOut(forward = true, slideDistance = NavigationAnimationValues.SlideDistance, durationMillis = NavigationAnimationValues.SlideDuration)
    }

    val popEnterTransition: () -> EnterTransition = {
        materialSharedAxisYIn(forward = false, slideDistance = NavigationAnimationValues.SlideDistance, durationMillis = NavigationAnimationValues.SlideDuration)
    }

    val popExitTransition: () -> ExitTransition = {
        materialSharedAxisYOut(forward = false, slideDistance = NavigationAnimationValues.SlideDistance, durationMillis = NavigationAnimationValues.SlideDuration)
    }
}