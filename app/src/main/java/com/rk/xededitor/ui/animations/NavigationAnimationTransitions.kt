package com.rk.xededitor.ui.animations

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut

object NavigationAnimationTransitions {

    val enterTransition: () -> EnterTransition = {
        materialSharedAxisXIn(forward = true, slideDistance = NavigationAnimationValues.SlideDistance, durationMillis = NavigationAnimationValues.SlideDuration)
    }

    val exitTransition: () -> ExitTransition = {
        materialSharedAxisXOut(forward = true, slideDistance = NavigationAnimationValues.SlideDistance, durationMillis = NavigationAnimationValues.SlideDuration)
    }

    val popEnterTransition: () -> EnterTransition = {
        materialSharedAxisXIn(forward = false, slideDistance = NavigationAnimationValues.SlideDistance, durationMillis = NavigationAnimationValues.SlideDuration)
    }

    val popExitTransition: () -> ExitTransition = {
        materialSharedAxisXOut(forward = false, slideDistance = NavigationAnimationValues.SlideDistance, durationMillis = NavigationAnimationValues.SlideDuration)
    }
}