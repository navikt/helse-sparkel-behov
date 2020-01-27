package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class SykepengehistorikkRiver(private val løser: Sykepengehistorikkløser) : River() {

    companion object {
        internal val behov = "Sykepengehistorikk"
    }

    init {
        validate { behov == it.path("@behov").asText() || behov in it.path("@behov").map(JsonNode::asText) }
        validate { it.path("@løsning").let { it.isMissingNode || it.isNull } }
        validate { it.hasNonNull("@id") }
        validate { it.hasNonNull("fødselsnummer") }
        validate { it.hasNonNull("utgangspunktForBeregningAvYtelse") }
    }

    override fun onPacket(packet: JsonNode, context: RapidsConnection.MessageContext) {
        løser.løsBehov(behov, packet, context)
    }
}
