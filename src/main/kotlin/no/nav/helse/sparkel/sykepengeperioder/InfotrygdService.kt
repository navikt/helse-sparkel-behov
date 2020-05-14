package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.InfotrygdClient
import org.slf4j.LoggerFactory

internal class InfotrygdService(private val infotrygdClient: InfotrygdClient) {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun løsningForBehov(
        packet: JsonMessage,
        behov: String,
        løsningMapperStrategy: (jsonNode: JsonNode) -> Any
    ): Map<String, List<Any>>? {
        try {
            val historikk = infotrygdClient.hentHistorikk(
                behovId = packet["@id"].asText(),
                vedtaksperiodeId = packet["vedtaksperiodeId"].asText(),
                fnr = packet["fødselsnummer"].asText(),
                fom = packet["historikkFom"].asLocalDate(),
                tom = packet["historikkTom"].asLocalDate(),
                mappingStrategy = løsningMapperStrategy
            )
            log.info(
                "løser behov: {} for {}",
                StructuredArguments.keyValue("id", packet["@id"].asText()),
                StructuredArguments.keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText())
            )
            sikkerlogg.info(
                "løser behov: {} for {}",
                StructuredArguments.keyValue("id", packet["@id"].asText()),
                StructuredArguments.keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText())
            )
            return mapOf(behov to historikk)
        } catch (err: Exception) {
            log.error(
                "feil ved henting av infotrygd-data: ${err.message} for {}",
                StructuredArguments.keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                err
            )
            sikkerlogg.error(
                "feil ved henting av infotrygd-data: ${err.message} for {}",
                StructuredArguments.keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                err
            )
            return null
        }
    }

}