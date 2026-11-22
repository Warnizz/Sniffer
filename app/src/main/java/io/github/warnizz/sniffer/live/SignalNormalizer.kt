package io.github.warnizz.sniffer.live

import io.github.warnizz.sniffer.towers.Radio

/** Maps a radio's dBm reading linearly onto 0..1. Unknown readings are neutral (0.5). */
object SignalNormalizer {
    fun normalize(radio: Radio, dbm: Int?): Float {
        if (dbm == null) return 0.5f
        val (lo, hi) = when (radio) {
            Radio.LTE, Radio.NR -> -140 to -44
            Radio.UMTS -> -120 to -24
            Radio.GSM, Radio.CDMA, Radio.UNKNOWN -> -113 to -51
        }
        return ((dbm - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    }
}
