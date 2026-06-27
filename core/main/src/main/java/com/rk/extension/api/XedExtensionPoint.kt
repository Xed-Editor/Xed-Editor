package com.rk.extension.api

/**
 * Annotation to mark entry points or extension points that are used or exposed to extensions.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.SOURCE)
annotation class XedExtensionPoint
