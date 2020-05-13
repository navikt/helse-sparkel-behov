package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

class Infotrygdperioder(jsonNode: JsonNode) {
    private val maksDato: LocalDate? = jsonNode["slutt"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) }
    val perioder = jsonNode["utbetalingList"].map { Infotrygdperiode(it, maksDato) }
}

data class Infotrygdperiode(private val jsonNode: JsonNode, val maksDato: LocalDate?) {
    val fom: LocalDate? = jsonNode["fom"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) }
    val tom: LocalDate? = jsonNode["tom"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) }
    val dagsats: Double = jsonNode["dagsats"].doubleValue()
    val utbetalingsGrad: String = jsonNode["utbetalingsGrad"].textValue()
}
