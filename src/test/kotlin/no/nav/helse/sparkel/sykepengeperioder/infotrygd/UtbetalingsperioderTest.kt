package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class UtbetalingsperioderTest {

    @Test
    fun utbetalingsperioder() {
        val json = readJson("infotrygdResponse.json")
        val utbetalingsperioder = Utbetalingsperioder(json["sykmeldingsperioder"][1])

        assertNotNull(utbetalingsperioder.perioder)
        assertEquals(12, utbetalingsperioder.perioder.size)
    }

    @Test
    fun `utbetalingsperioder med manglende fom og tom i utbetalingslisten`() {
        val json = readJson("infotrygdResponseMissingFomAndTom.json")
        val utbetalingsperioder = Utbetalingsperioder(json["sykmeldingsperioder"][1])

        assertNotNull(utbetalingsperioder.perioder)
        assertEquals(12, utbetalingsperioder.perioder.size)
        Assertions.assertNull(utbetalingsperioder.perioder[1].fom)
        Assertions.assertNull(utbetalingsperioder.perioder[2].tom)
    }

    @Test
    fun `parser alle perioders utbetalingslister`() {
        val json = readJson("infotrygdResponse.json")
        val utbetalingsperioder = json["sykmeldingsperioder"].map { Utbetalingsperioder(it) }
        assertEquals(20, utbetalingsperioder.size)
        assertEquals(69, utbetalingsperioder.flatMap { it.perioder }.size)
    }

    @Test
    fun `parser alle felter for utbetalingsperiode`() {
        val json = readJson("infotrygdResponse.json")
        val utbetalingsperioderList = json["sykmeldingsperioder"].map { Utbetalingsperioder(it) }

        val enTomUtbetalingsperiode = utbetalingsperioderList.first().perioder.size
        assertEquals(0, enTomUtbetalingsperiode)

        val enIkkeTomUtbetalingsperiode = utbetalingsperioderList[1].perioder.first()
        assertEquals(LocalDate.of(2019, 3, 18), enIkkeTomUtbetalingsperiode.fom)
        assertEquals(LocalDate.of(2019, 4, 11), enIkkeTomUtbetalingsperiode.tom)
        assertEquals("050", enIkkeTomUtbetalingsperiode.grad)
        assertEquals(765.0, enIkkeTomUtbetalingsperiode.dagsats)
        assertEquals("ArbRef", enIkkeTomUtbetalingsperiode.typetekst)
        assertEquals("88888888", enIkkeTomUtbetalingsperiode.organisasjonsnummer)
    }

    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    private fun readJson(fileName: String) =
        objectMapper.readTree(this::class.java.getResource("/$fileName").readText())
}