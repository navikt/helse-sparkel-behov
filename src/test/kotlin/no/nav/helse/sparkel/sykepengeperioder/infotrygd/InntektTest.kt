package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import no.nav.helse.sparkel.sykepengeperioder.infotrygd.Inntektsopplysninger.PeriodeKode.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.roundToInt

class InntektTest {

    @Test
    fun `kan regne om daglig inntekt`() {
        assertInntekt(10833.33, 500.0, Daglig)
        assertInntekt(21666.67, 5000.0, Ukentlig)
        assertInntekt(21666.67, 10000.0, Biukentlig)
        assertInntekt(39000.00, 39000.0, Månedlig)
        assertInntekt(43333.33, 520000.0, Årlig)
        assertInntekt(32083.33, 385000.0, SkjønnsmessigFastsatt)
        assertInntekt(34666.67, 416000.0, Premiegrunnlag)
        assertInntekt(41666.67, 500000.0, Årlig)
        assertInntekt(26017.33, 6004.0, Ukentlig)
        assertInntekt(26745.33, 1234.4, Daglig)
    }

    @Test
    fun `kaster feil ved andre periodetyper`() {
        assertThrows<IllegalArgumentException> { Inntektsopplysninger.PeriodeKode.verdiFraKode("I") }
    }

    private fun assertInntekt(expected: Double, lønn: Double, kode: Inntektsopplysninger.PeriodeKode) {
        assertEquals(expected, (kode.omregn(lønn) * 100).roundToInt() / 100.0)
    }
}
