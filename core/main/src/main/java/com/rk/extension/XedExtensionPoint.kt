package com.rk.extension

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class XedExtensionPoint

//todo add r8 rules to keep classes/function/propertie if this annotation is set