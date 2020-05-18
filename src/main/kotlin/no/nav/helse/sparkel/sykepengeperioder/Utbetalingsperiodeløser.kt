package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.Utbetalingsperioder
import org.slf4j.LoggerFactory

private val objectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())

internal class Utbetalingsperiodeløser(
    rapidsConnection: RapidsConnection,
    private val infotrygdService: InfotrygdService
) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    companion object {
        const val behov = "HentInfotrygdutbetalinger"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(behov)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
            //TODO: når alle "gamle" behov er lest inn, skal ordentlig validering på plass igjen
//            validate { it.require("$behov.historikkFom", JsonNode::asLocalDate) }
//            validate { it.require("$behov.historikkTom", JsonNode::asLocalDate) }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        sikkerlogg.error("forstod ikke $behov med melding\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val json = objectMapper.readTree(packet.toJson())
        val historikkFom =
            if (!json.path("$behov.historikkFom").isMissingOrNull()) json.path("$behov.historikkFom").asLocalDate()
            else json[behov]["historikkFom"].asLocalDate()
        val historikkTom =
            if (!json.path("$behov.historikkTom").isMissingOrNull()) json.path("$behov.historikkTom").asLocalDate()
            else json[behov]["historikkTom"].asLocalDate()
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        infotrygdService.løsningForBehov(
            packet["@id"].asText(),
            packet["vedtaksperiodeId"].asText(),
            packet["fødselsnummer"].asText(),
            historikkFom,
            historikkTom
        )?.let { løsning ->
            packet["@løsning"] = mapOf(
                behov to løsning.flatMap { Utbetalingsperioder(it).perioder }
            )
            context.send(packet.toJson().also { json ->
                sikkerlogg.info(
                    "sender svar {} for {}:\n\t{}",
                    keyValue("id", packet["@id"].asText()),
                    keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                    json
                )
            })
        }
    }
}

