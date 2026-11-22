package io.github.snifferapp.sniffer.sound

/** Control values sent to the Pd patch once per tick. Receive names are fixed (see design doc). */
data class SoundControls(
    val density: Float,
    val nearestM: Float,
    val signal: Float,
    val netType: Int,
    val neighbours: Int,
    val handover: Boolean,
    val neighbourEvent: Boolean,
    val master: Float,
)

sealed interface PdMessage {
    val receiver: String
    data class Float(override val receiver: String, val value: kotlin.Float) : PdMessage
    data class Bang(override val receiver: String) : PdMessage
}

fun SoundControls.toPdMessages(): List<PdMessage> = buildList {
    add(PdMessage.Float("density", density))
    add(PdMessage.Float("nearest", nearestM))
    add(PdMessage.Float("signal", signal))
    add(PdMessage.Float("nettype", netType.toFloat()))
    add(PdMessage.Float("neighbours", neighbours.toFloat()))
    add(PdMessage.Float("master", master))
    if (handover) add(PdMessage.Bang("handover"))
    if (neighbourEvent) add(PdMessage.Bang("neighbour_event"))
}

interface SoundSink {
    fun start()
    fun stop()
    fun send(c: SoundControls)
}
