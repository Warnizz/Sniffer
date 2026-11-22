package io.github.warnizz.sniffer.sound

import kotlin.math.exp

/** Exponential smoothing with time constant [tauMs]; the first value passes straight through. */
class Smoother(private val tauMs: Double = 20_000.0) {
    private var value: Float? = null
    private var lastMs = 0L

    fun update(target: Float, nowMs: Long): Float {
        val v = value
        val next = if (v == null) target else {
            val dt = (nowMs - lastMs).coerceAtLeast(0L)
            v + (target - v) * (1 - exp(-dt / tauMs)).toFloat()
        }
        value = next
        lastMs = nowMs
        return next
    }
}
