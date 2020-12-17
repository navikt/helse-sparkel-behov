package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.Utbetalingshistorikk
import org.slf4j.LoggerFactory

internal class Sykepengehistorikkløser(
    rapidsConnection: RapidsConnection,
    private val infotrygdService: InfotrygdService
) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    companion object {
        const val behov = "Sykepengehistorikk"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(behov)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.require("$behov.historikkFom", JsonNode::asLocalDate) }
            validate { it.require("$behov.historikkTom", JsonNode::asLocalDate) }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        sikkerlogg.error("forstod ikke $behov med melding\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        infotrygdService.løsningForBehov(
            packet["@id"].asText(),
            packet["fødselsnummer"].asText(),
            packet["$behov.historikkFom"].asLocalDate(),
            packet["$behov.historikkTom"].asLocalDate()
        )?.let { løsning ->
            packet["@løsning"] = mapOf(
                behov to løsning
                        .sortedByDescending { it["sykemeldtFom"].asLocalDate() }
                        .mapIndexed { index, jsonNode -> Utbetalingshistorikk(jsonNode, index == 0) }
            )
            context.send(packet.toJson().also { json ->
                sikkerlogg.info(
                    "sender svar {}:\n\t{}",
                    keyValue("id", packet["@id"].asText()),
                    json
                )
            })
        }
    }
}
