package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.InfotrygdClient
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.infotrygdperioder
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.utbetalingshistorikk
import org.slf4j.LoggerFactory

internal class InfotrygdRiver(
    rapidsConnection: RapidsConnection,
    private val infotrygdClient: InfotrygdClient
) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate { it.requireAny("@behov", listOf("Sykepengehistorikk")) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
            validate { it.require("historikkFom", JsonNode::asLocalDate) }
            validate { it.require("historikkTom", JsonNode::asLocalDate) }
        }.register(this)

        River(rapidsConnection).apply {
            validate { it.requireAny("@behov", listOf("Infotrygdperioder")) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
            validate { it.require("historikkFom", JsonNode::asLocalDate) }
            validate { it.require("historikkTom", JsonNode::asLocalDate) }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        sikkerlogg.error("forstod ikke behov med melding\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        try {
            val behov = Behov.valueOf(packet["@behov"].asText())
            infotrygdClient.hentHistorikk(
                behovId = packet["@id"].asText(),
                vedtaksperiodeId = packet["vedtaksperiodeId"].asText(),
                fnr = packet["fødselsnummer"].asText(),
                fom = packet["historikkFom"].asLocalDate(),
                tom = packet["historikkTom"].asLocalDate(),
                mappingStrategy = behov.mappingStrategy
            ).also { packet.setLøsning(behov.name, it) }
            log.info(
                "løser behov: {} for {}",
                keyValue("id", packet["@id"].asText()),
                keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText())
            )
            sikkerlogg.info(
                "løser behov: {} for {}",
                keyValue("id", packet["@id"].asText()),
                keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText())
            )

            context.send(packet.toJson().also {
                sikkerlogg.info(
                    "sender svar {} for {}:\n\t{}",
                    keyValue("id", packet["@id"].asText()),
                    keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                    it
                )
            })
        } catch (err: Exception) {
            log.error(
                "feil ved henting av infotrygd-data: ${err.message} for {}",
                keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                err
            )
            sikkerlogg.error(
                "feil ved henting av infotrygd-data: ${err.message} for {}",
                keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                err
            )
        }
    }

    private fun JsonMessage.setLøsning(nøkkel: String, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel to data
        )
    }

}

enum class Behov(val mappingStrategy: (jsonNode: JsonNode) -> Any) {
    Sykepengehistorikk(JsonNode::utbetalingshistorikk),
    Infotrygdperioder(JsonNode::infotrygdperioder)
}