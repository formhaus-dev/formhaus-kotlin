package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine

@RendererApi
public val FormEngine.version: Int get() = core.version

@RendererApi
public val FormEngine.errorsVersion: Int get() = core.errorsGeneration

@RendererApi
public val FormEngine.definitionRevision: Int get() = core.definitionGeneration

@RendererApi
public val FormEngine.restarts: Int get() = core.restarts

@RendererApi
public val FormEngine.sending: Boolean get() = core.submitting && core.sending == core.latestOperation
