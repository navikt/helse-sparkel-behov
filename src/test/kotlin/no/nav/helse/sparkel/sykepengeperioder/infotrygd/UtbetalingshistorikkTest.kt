package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

internal class UtbetalingshistorikkTest {

    @Test
    internal fun utbetalingshistorikk() {
        val jsonNode = readJson()
        val utbetalingshistorikk = Utbetalingshistorikk(jsonNode["sykmeldingsperioder"][1])

        assertEquals(LocalDate.of(2018, 12, 3), utbetalingshistorikk.fom)
        assertEquals(LocalDate.of(2019, 4, 11), utbetalingshistorikk.tom)
        assertEquals("050", utbetalingshistorikk.grad)
        assertNotNull(utbetalingshistorikk.inntektsopplysninger)
        assertNotNull(utbetalingshistorikk.utbetalteSykeperioder)

    }

    private val objectMapper = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())

    private fun readJson() = objectMapper.readTree(
        File("src/test/resources/infotrygdResponse.json").readText()
    )
}
