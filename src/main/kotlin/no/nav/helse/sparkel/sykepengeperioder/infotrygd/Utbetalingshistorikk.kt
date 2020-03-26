package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class Utbetalingshistorikk(jsonNode: JsonNode) {
    companion object {
        internal val log = LoggerFactory.getLogger(Utbetalingshistorikk::class.java)
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
    }

    private val gyldigePeriodeKoder = listOf("D", "U", "F", "M", "Å", "X", "Y")
    val inntektsopplysninger: List<Inntektsopplysninger> = jsonNode["inntektList"]
        .filter {
            when (val periodeKode = it["periodeKode"].textValue()) {
                in gyldigePeriodeKoder -> true
                else -> {
                    log.warn("Ukjent periodetype i respons fra Infotrygd: $periodeKode")
                    tjenestekallLog.warn("Ukjent periodetype i respons fra Infotrygd: $periodeKode")
                    false
                }
            }
        }
        .map { Inntektsopplysninger(it) }
        .filter(Inntektsopplysninger::skalTilSpleis)

    val utbetalteSykeperioder = jsonNode["utbetalingList"].map { Utbetaling(it, inntektsopplysninger) }
}

data class Utbetaling(
    private val jsonNode: JsonNode,
    private val inntektsopplysninger: List<Inntektsopplysninger>
) {
    val fom: LocalDate? = jsonNode["fom"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) }
    val tom: LocalDate? = jsonNode["tom"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) }
    val utbetalingsGrad: String = jsonNode["utbetalingsGrad"].textValue()
    val oppgjorsType: String = jsonNode["oppgjorsType"].textValue()
    val utbetalt: LocalDate? = jsonNode["utbetalt"].takeUnless { it.isNull }?.let { LocalDate.parse(it.textValue()) }
    val dagsats: Double = jsonNode["dagsats"].doubleValue()
    val typeKode: String = jsonNode["typeKode"].textValue()
    val typeTekst: String = jsonNode["typeTekst"].textValue()
    val orgnummer: String = jsonNode["arbOrgnr"].asText()
}

data class Inntektsopplysninger(private val jsonNode: JsonNode) {
    private val periodeKode = PeriodeKode.verdiFraKode(jsonNode["periodeKode"].textValue())
    private val lønn = jsonNode["loenn"].decimalValue()

    val sykepengerFom: LocalDate = LocalDate.parse(jsonNode["sykepengerFom"].textValue())
    val inntekt: Int = periodeKode.omregn(lønn)
    val orgnummer: String = jsonNode["orgNr"].textValue()
    val refusjonTom: LocalDate? =
        jsonNode["refusjonTom"].takeUnless { it.isNull }?.let { LocalDate.parse(it.textValue()) }

    internal fun skalTilSpleis() = periodeKode != PeriodeKode.Premiegrunnlag

    internal enum class PeriodeKode(
        val fraksjon: BigDecimal,
        val kode: String
    ) {
        Daglig(260.0.toBigDecimal().setScale(10) / 12.0.toBigDecimal(), "D"),
        Ukentlig(52.0.toBigDecimal().setScale(10) / 12.0.toBigDecimal(), "U"),
        Biukentlig(26.0.toBigDecimal().setScale(10) / 12.0.toBigDecimal(), "F"),
        Månedlig(1.0.toBigDecimal().setScale(10), "M"),
        Årlig(1.0.toBigDecimal().setScale(10) / 12.0.toBigDecimal(), "Å"),
        SkjønnsmessigFastsatt(1.0.toBigDecimal().setScale(10) / 12.0.toBigDecimal(), "X"),
        Premiegrunnlag(1.0.toBigDecimal().setScale(10) / 12.0.toBigDecimal(), "Y");

        fun omregn(lønn: BigDecimal): Int = (lønn * fraksjon).setScale(0, RoundingMode.HALF_UP).toInt()

        companion object {
            fun verdiFraKode(kode: String): PeriodeKode {
                return values().find { it.kode == kode } ?: throw IllegalArgumentException("Ukjent kodetype $kode")
            }
        }
    }
}
