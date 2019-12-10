package no.nav.helse.sparkel.sykepengeperioder

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SykepengerperioderStreamTest  {
    @Test
    fun `Test om behov inneholder ønsket string` (){
        val behovMedListe = objectMapper.readTree("""{"@id": "behovsid", "aktørId": "aktørid1", "@behov": ["Sykepengehistorikk", "Foreldrepenger"]}""")
        val behovUtenListe = objectMapper.readTree("""{"@id": "behovsid", "aktørId": "aktørid1", "@behov": "Sykepengehistorikk"}""")
        val behovUtenSykepenger = objectMapper.readTree("""{"@id": "behovsid", "aktørId": "aktørid1", "@behov": "Foreldrepenger"}""")
        assertTrue(behovMedListe.skalOppfyllesAvOss("Sykepengehistorikk"))
        assertTrue(behovUtenListe.skalOppfyllesAvOss("Sykepengehistorikk"))
        assertFalse(behovUtenSykepenger.skalOppfyllesAvOss("Sykepengehistorikk"))
    }
}
