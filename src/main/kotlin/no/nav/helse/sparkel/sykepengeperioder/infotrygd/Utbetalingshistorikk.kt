package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.Inntektsopplysninger.PeriodeKode.*
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.Utbetalingshistorikk.Companion.log
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal class Utbetalingshistorikk(private val jsonNode: JsonNode) {
    companion object {
        internal val log = LoggerFactory.getLogger(Utbetalingshistorikk::class.java)
    }

    private val gyldigePeriodeKoder = listOf(D, U, F, M, Å)
    private val ugyldigePeriodeKoder = listOf(X, Y)

    internal val fom: LocalDate = LocalDate.parse(jsonNode["sykemeldtFom"].textValue())
    internal val tom: LocalDate = LocalDate.parse(jsonNode["sykemeldtTom"].textValue())
    internal val grad: String = jsonNode["grad"].textValue()
    internal val inntektsopplysninger: List<Inntektsopplysninger> = jsonNode["inntektList"]
            .also { if(valueOf(it["periodeKode"].textValue()) !in gyldigePeriodeKoder) println(it) }
            .filter { valueOf(it["periodeKode"].textValue()) in gyldigePeriodeKoder }
            .map { Inntektsopplysninger(it) }
    internal val utbetalteSykeperioder: List<UtbetaltSykeperiode> = jsonNode["utbetalingList"]
            .filter { it["typeTekst"].textValue() == "ArbRef" }
            .map { UtbetaltSykeperiode(it, inntektsopplysninger) }
}

internal class UtbetaltSykeperiode(jsonNode: JsonNode, inntektsopplysninger: List<Inntektsopplysninger>) {
    internal val fom: LocalDate = LocalDate.parse(jsonNode["fom"].textValue())
    internal val tom: LocalDate = LocalDate.parse(jsonNode["tom"].textValue())
    internal val grad: String = jsonNode["utbetalingsGrad"].textValue()
    internal val dagsats: Double = jsonNode["dagsats"].asDouble()
    internal val inntektPerDag: Double? = inntektsopplysninger.sortedBy { it.dato }.last { !fom.isBefore(it.dato) }.utledInntektPerDag()
    internal val orgnummer: String = jsonNode["arbOrgnr"].asText()
}

internal class Inntektsopplysninger(jsonNode: JsonNode) {
    internal val dato: LocalDate = LocalDate.parse(jsonNode["sykepengerFom"].textValue())
    internal val inntekt: Double = jsonNode["loenn"].asDouble()
    internal val orgnummer: String = jsonNode["orgNr"].textValue()
    internal val periodeKode: PeriodeKode = valueOf(jsonNode["periodeKode"].textValue())

    internal enum class PeriodeKode {D, U, F, M, Å, X, Y}

    internal fun utledInntektPerDag() =
            when (periodeKode) {
                D -> inntekt
                U -> inntekt * 52 / 260
                F -> inntekt * 26 / 260
                M -> inntekt * 12 / 260
                Å -> inntekt / 260
                else -> {
                    log.warn("Ukjent periodetype i respons fra Infotrygd: $periodeKode")
                    null
                }
            }
}

