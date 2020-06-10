package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.InfotrygdClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal class InfotrygdService(private val infotrygdClient: InfotrygdClient) {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun løsningForBehov(
        behovId: String,
        vedtaksperiodeId: String,
        fødselsnummer: String,
        fom: LocalDate,
        tom: LocalDate
    ): JsonNode? {
        try {
            val historikk = infotrygdClient.hentHistorikk(
                behovId = behovId,
                vedtaksperiodeId = vedtaksperiodeId,
                fnr = fødselsnummer,
                fom = fom,
                tom = tom
            )
            log.info(
                "løser behov: {} for {}",
                keyValue("id", behovId),
                keyValue("vedtaksperiodeId", vedtaksperiodeId)
            )
            sikkerlogg.info(
                "løser behov: {} for {}",
                keyValue("id", behovId),
                keyValue("vedtaksperiodeId", vedtaksperiodeId)
            )
            return historikk
        } catch (err: Exception) {
            log.warn(
                "feil ved henting av infotrygd-data: ${err.message} for {}",
                keyValue("vedtaksperiodeId", vedtaksperiodeId),
                err
            )
            sikkerlogg.warn(
                "feil ved henting av infotrygd-data: ${err.message} for {}",
                keyValue("vedtaksperiodeId", vedtaksperiodeId),
                err
            )
            return null
        }
    }

}
