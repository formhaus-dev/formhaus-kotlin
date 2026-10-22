package dev.formhaus.research

import dev.formhaus.core.engine.FormResearch
import dev.formhaus.core.model.FieldOption
import java.security.SecureRandom

public class Research : FormResearch {
    override fun seed(): String =
        (0 until 2).joinToString("") {
            java.lang.Long
                .toHexString(random.nextLong())
                .padStart(16, '0')
        }

    override fun order(
        seed: String,
        key: String,
        options: List<FieldOption>,
    ): List<FieldOption> = optionOrder(seed, key, options.map { it.pinned || it.exclusive }).map(options::get)

    private companion object {
        val random = SecureRandom()
    }
}

internal fun optionOrder(
    seed: String,
    key: String,
    pinned: List<Boolean>,
): List<Int> {
    var state = 0x811C9DC5.toInt()
    val mix = { unit: Char -> state = (state xor unit.code) * 0x01000193 }
    seed.forEach(mix)
    mix('\u001F')
    key.forEach(mix)
    val order = pinned.indices.toMutableList()
    val slots = order.filter { !pinned[it] }
    for (i in slots.size - 1 downTo 1) {
        state += 0x6D2B79F5
        var t = (state xor (state ushr 15)) * (state or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        val j = slots[((t xor (t ushr 14)).toUInt() % (i + 1).toUInt()).toInt()]
        order[slots[i]] = order[j].also { order[j] = order[slots[i]] }
    }
    return order
}
