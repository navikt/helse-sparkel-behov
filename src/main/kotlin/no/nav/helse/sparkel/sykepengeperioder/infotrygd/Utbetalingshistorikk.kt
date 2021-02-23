package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import java.time.LocalDate

class Utbetalingshistorikk(jsonNode: JsonNode, sjekkStatslønn: Boolean = false) {
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

    private val ferie1: Utbetaling? = jsonNode["ferie1Fom"]?.takeUnless { it.isNull }?.textValue()?.let(LocalDate::parse)?.let { ferie1fom ->
        jsonNode["ferie1Tom"]?.takeUnless { it.isNull }?.textValue()?.let(LocalDate::parse)?.let { ferie1tom ->
            Utbetaling(ferie1fom, ferie1tom, "", "", ferie1fom, 0.0, "9", "Ferie", "0")
        }
    }

    private val ferie2: Utbetaling? = jsonNode["ferie2Fom"]?.takeUnless { it.isNull }?.textValue()?.let(LocalDate::parse)?.let { ferie2fom ->
        jsonNode["ferie2Tom"]?.takeUnless { it.isNull }?.textValue()?.let(LocalDate::parse)?.let { ferie2tom ->
            Utbetaling(ferie2fom, ferie2tom, "", "", ferie2fom, 0.0, "9", "Ferie", "0")
        }
    }

    val utbetalteSykeperioder = jsonNode["utbetalingList"].map { Utbetaling(it) } + listOfNotNull(ferie1, ferie2)
    val maksDato: LocalDate? = jsonNode["slutt"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) }
    val statslønn: Boolean = sjekkStatslønn && !jsonNode.path("statslonnList").isEmpty
}

data class Utbetaling(
    val fom: LocalDate?,
    val tom: LocalDate?,
    val utbetalingsGrad: String,
    val oppgjorsType: String,
    val utbetalt: LocalDate?,
    val dagsats: Double,
    val typeKode: String,
    val typeTekst: String,
    val orgnummer: String
) {
    constructor(jsonNode: JsonNode) : this(
        fom = jsonNode["fom"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) },
        tom = jsonNode["tom"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) },
        utbetalingsGrad = jsonNode["utbetalingsGrad"].textValue(),
        oppgjorsType = jsonNode["oppgjorsType"].textValue(),
        utbetalt = jsonNode["utbetalt"].takeUnless { it.isNull }?.let { LocalDate.parse(it.textValue()) },
        dagsats = jsonNode["dagsats"].doubleValue(),
        typeKode = jsonNode["typeKode"].textValue(),
        typeTekst = jsonNode["typeTekst"].textValue(),
        orgnummer = jsonNode["arbOrgnr"].asText()
    )
}

data class Inntektsopplysninger(private val jsonNode: JsonNode) {
    private val periodeKode = PeriodeKode.verdiFraKode(jsonNode["periodeKode"].textValue())
    private val lønn = jsonNode["loenn"].doubleValue()

    val sykepengerFom: LocalDate = LocalDate.parse(jsonNode["sykepengerFom"].textValue())
    val inntekt: Double = periodeKode.omregn(lønn)
    val orgnummer: String = jsonNode["orgNr"].textValue()
    val refusjonTom: LocalDate? =
        jsonNode["refusjonTom"].takeUnless { it.isNull }?.let { LocalDate.parse(it.textValue()) }
    val refusjonTilArbeidsgiver = "J" == jsonNode.path("refusjonsType").asText()

    internal fun skalTilSpleis() = periodeKode != PeriodeKode.Premiegrunnlag

    internal enum class PeriodeKode(
        val fraksjon: Double,
        val kode: String
    ) {
        Daglig(260.0 / 12.0, "D"),
        Ukentlig(52.0 / 12.0, "U"),
        Biukentlig(26.0 / 12.0, "F"),
        Månedlig(1.0, "M"),
        Årlig(1.0 / 12.0, "Å"),
        SkjønnsmessigFastsatt(1.0 / 12.0, "X"),
        Premiegrunnlag(1.0 / 12.0, "Y");

        fun omregn(lønn: Double): Double = (lønn * fraksjon)

        companion object {
            fun verdiFraKode(kode: String): PeriodeKode {
                return values().find { it.kode == kode } ?: throw IllegalArgumentException("Ukjent kodetype $kode")
            }
        }
    }
}
