package io.github.snifferapp.sniffer.live

import io.github.snifferapp.sniffer.towers.Radio

/** A cell the phone hears right now. Null fields are unknown to the modem. */
data class LiveCell(
    val radio: Radio,
    val mcc: Int?,
    val mnc: Int?,
    val area: Int?,
    val cellId: Long?,
    val pci: Int?,
    val dbm: Int?,
    val serving: Boolean,
) {
    /** Stable identity: the full cell key when known, otherwise radio + PCI. */
    val key: String
        get() = if (cellId != null) "$radio-$mcc-$mnc-$area-$cellId" else "$radio-pci-$pci"
}

/** netType: 0 = no service, 2/3/4/5 = 2G/3G/4G/5G. */
data class LiveSnapshot(
    val serving: LiveCell?,
    val neighbours: List<LiveCell>,
    val netType: Int,
    val timeMs: Long,
) {
    val signal: Float get() = serving?.let { SignalNormalizer.normalize(it.radio, it.dbm) } ?: 0.5f

    companion object {
        val EMPTY = LiveSnapshot(null, emptyList(), 0, 0)
    }
}

fun netTypeOf(radio: Radio?): Int = when (radio) {
    Radio.GSM, Radio.CDMA -> 2
    Radio.UMTS -> 3
    Radio.LTE -> 4
    Radio.NR -> 5
    Radio.UNKNOWN, null -> 0
}
