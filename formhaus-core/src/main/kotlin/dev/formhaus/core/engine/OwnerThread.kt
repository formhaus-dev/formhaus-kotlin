package dev.formhaus.core.engine

internal class OwnerThread(
    enabled: Boolean,
) {
    private val owner: Thread? = if (enabled) Thread.currentThread() else null

    fun check(action: String) {
        val owner = owner ?: return
        val current = Thread.currentThread()
        check(current === owner) {
            "FormEngine.$action() was called on thread \"${current.name}\", but the engine was created on " +
                "thread \"${owner.name}\". Call FormEngine only from the thread that created it."
        }
    }
}
