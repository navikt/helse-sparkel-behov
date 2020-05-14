package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

class Utbetalingsperioder(jsonNode: JsonNode) {
    val perioder = jsonNode["utbetalingList"].map { Utbetalingsperiode(it) }
}

data class Utbetalingsperiode(private val jsonNode: JsonNode) {
    val fom: LocalDate? = jsonNode["fom"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) }
    val tom: LocalDate? = jsonNode["tom"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) }
    val dagsats: Double = jsonNode["dagsats"].doubleValue()
    val grad: String = jsonNode["utbetalingsGrad"].textValue()
    val typetekst: String? = jsonNode["typeTekst"]?.takeUnless { it.isNull }?.textValue()
    val organisasjonsnummer: String? = jsonNode["arbOrgnr"]?.takeUnless { it.isNull }?.asText()
}
