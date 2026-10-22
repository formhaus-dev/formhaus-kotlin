package dev.formhaus.compose.model

import android.view.Choreographer
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormSnapshot
import dev.formhaus.core.engine.snapshot

public typealias SnapshotChangeFn = (FormSnapshot?) -> Unit

internal class SnapshotEmitter(
    private val engine: FormEngine,
    var handler: SnapshotChangeFn?,
) {
    private var last: FormSnapshot? = engine.snapshot()
    private var pending = false
    private var closed = false
    var cleared: Boolean = false
        private set
    private val frame = Choreographer.FrameCallback { emit() }

    fun changed() {
        cleared = false
        if (closed || pending || handler == null) return
        pending = true
        Choreographer.getInstance().postFrameCallback(frame)
    }

    fun submitted() {
        cancel()
        cleared = true
        if (closed) return
        last = null
        handler?.invoke(null)
    }

    fun close() {
        cancel()
        closed = true
    }

    private fun cancel() {
        if (!pending) return
        pending = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    private fun emit() {
        pending = false
        if (closed) return
        val next = engine.snapshot()
        if (next == last) return
        last = next
        handler?.invoke(next)
    }
}
