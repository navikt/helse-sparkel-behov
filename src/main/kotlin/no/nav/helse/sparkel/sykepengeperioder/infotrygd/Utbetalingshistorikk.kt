package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

internal class Utbetalingshistorikk(private val jsonNode: JsonNode) {
    private val fom: LocalDate = LocalDate.parse(jsonNode["sykemeldtFom"].textValue())
    private val tom: LocalDate = LocalDate.parse(jsonNode["sykemeldtTom"].textValue())
    private val grad: String = jsonNode["grad"].textValue()
    private val inntektsopplysninger: List<Inntektsopplysninger> = jsonNode["inntektList"]
        .map { Inntektsopplysninger(it) }
    private val utbetalteSykeperioder: List<UtbetaltSykeperiode> = jsonNode["utbetalingList"]
        .filter { it["typeTekst"].textValue() == "ArbRef" }
        .map { UtbetaltSykeperiode(it, inntektsopplysninger) }
}

internal class UtbetaltSykeperiode(jsonNode: JsonNode, inntektsopplysninger: List<Inntektsopplysninger>) {
    private val fom: LocalDate = LocalDate.parse(jsonNode["fom"].textValue())
    private val tom: LocalDate = LocalDate.parse(jsonNode["tom"].textValue())
    private val grad: String = jsonNode["utbetalingsGrad"].textValue()
    private val dagsats: Double = jsonNode["dagsats"].asDouble()
    private val inntektPerDag: Double = jsonNode["dagsats"].asDouble() // TODO
    private val orgnummer: String = jsonNode["arbOrgnr"].textValue()
}

internal class Inntektsopplysninger(jsonNode: JsonNode) {
    private val dato: LocalDate = LocalDate.parse(jsonNode["sykepengerFom"].textValue())
    private val inntekt: Double = jsonNode["loenn"].asDouble()
    private val orgnummer: String = jsonNode["orgNr"].textValue()

    private fun utledInntektPerDag(jsonNode: JsonNode) {
        // uke = uke * 52 / 260
        // biuke = biuke * 26 / 260
        // måned = måned * 12 / 260
        // år = år / 260
    }
}

