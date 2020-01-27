package no.nav.helse.sparkel.sykepengeperioder

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.RapidsConnection
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SykepengehistorikkRiverTest {

    @Test
    internal fun `Test om behov inneholder ønsket string` () {
        assertInvalid("""{}""")
        assertInvalid("""{"@id": "id", "fødselsnummer": "fnr", "@behov": "Foreldrepenger", "utgangspunktForBeregningAvYtelse": "foo"}""")
        assertInvalid("""{"@id": "id", "@behov": "Sykepengehistorikk", "utgangspunktForBeregningAvYtelse": "foo"}""")
        assertInvalid("""{"@id": "id", "@behov": ["Sykepengehistorikk"], "utgangspunktForBeregningAvYtelse": "foo"}""")
        assertValid("""{"@id": "id", "fødselsnummer": "fnr", "@behov": ["Sykepengehistorikk", "Foreldrepenger"], "utgangspunktForBeregningAvYtelse": "foo"}""")
        assertValid("""{"@id": "id", "fødselsnummer": "fnr", "@behov": "Sykepengehistorikk", "utgangspunktForBeregningAvYtelse": "foo"}""")
    }

    private fun assertValid(message: String) {
        assertTrue(testMelding(message))
    }

    private fun assertInvalid(message: String) {
        assertFalse(testMelding(message))
    }

    private fun testMelding(message: String): Boolean {
        val løser = mockk<Sykepengehistorikkløser>()
        var called = false
        every {
            løser.løsBehov(any(), any(), any())
        } answers {
            called = true
        }

        SykepengehistorikkRiver(løser).onMessage(message, object : RapidsConnection.MessageContext {
            override fun send(message: String) {}

            override fun send(key: String, message: String) {}
        })

        return called
    }
}
