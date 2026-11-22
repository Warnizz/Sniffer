package io.github.warnizz.sniffer.live

/** Detects serving-cell changes (rate-limited) and newly heard neighbours. */
class HandoverDetector(private val minIntervalMs: Long = 10_000) {
    private var lastServing: String? = null
    private var lastFiredMs: Long? = null
    private var lastNeighbours: Set<String>? = null

    fun onSnapshot(s: LiveSnapshot): Boolean {
        val key = s.serving?.key ?: return false
        val prev = lastServing
        lastServing = key
        if (prev == null || prev == key) return false
        val last = lastFiredMs
        if (last != null && s.timeMs - last < minIntervalMs) return false
        lastFiredMs = s.timeMs
        return true
    }

    fun newNeighbours(s: LiveSnapshot): Int {
        val keys = s.neighbours.map { it.key }.toSet()
        val prev = lastNeighbours
        lastNeighbours = keys
        return if (prev == null) 0 else (keys - prev).size
    }
}
