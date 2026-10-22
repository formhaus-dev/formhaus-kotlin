package dev.formhaus.core.renderer

@RequiresOptIn(message = "Renderer support API for Formhaus renderers; it may change without notice.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
public annotation class RendererApi
