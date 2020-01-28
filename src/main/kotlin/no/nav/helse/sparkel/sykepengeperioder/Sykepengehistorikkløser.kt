package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.toJson
import no.nav.helse.sparkel.sykepengeperioder.SykepengehistorikkRiver.Companion.behov
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.InfotrygdClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal class Sykepengehistorikkløser(private val infotrygdClient: InfotrygdClient): River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)
    private val objectMapper = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())

    override fun onPacket(packet: JsonNode, context: RapidsConnection.MessageContext) {
        try {
            sikkerlogg.info("mottok melding: ${packet.toJson()}")
            infotrygdClient.hentHistorikk(
                    fnr = packet["fødselsnummer"].asText(),
                    datoForYtelse = LocalDate.parse(packet["utgangspunktForBeregningAvYtelse"].asText())
            ).also {
                packet.setLøsning(behov, it)
            }
            log.info("løser behov: ${packet["@id"].textValue()}")

            context.send(packet.toJson())
        } catch (err: Exception) {
            log.error("feil ved henting av infotrygd-data: ${err.message}", err)
        }
    }

    private fun JsonNode.setLøsning(nøkkel: String, data: Any) =
            (this as ObjectNode).set<JsonNode>("@løsning", objectMapper.convertValue(mapOf(
                    nøkkel to data
            )))

}
