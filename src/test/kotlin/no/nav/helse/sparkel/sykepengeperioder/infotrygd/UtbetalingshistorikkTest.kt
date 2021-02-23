package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class UtbetalingshistorikkTest {

    @Test
    fun utbetalingshistorikk() {
        val json = readJson("infotrygdResponse.json")
        val utbetalingshistorikk = Utbetalingshistorikk(json["sykmeldingsperioder"][1])

        assertNotNull(utbetalingshistorikk.inntektsopplysninger)
        assertNotNull(utbetalingshistorikk.utbetalteSykeperioder)
        assertEquals(LocalDate.of(2019, 11, 8), utbetalingshistorikk.maksDato)
        assertEquals(12, utbetalingshistorikk.utbetalteSykeperioder.size)
        assertEquals("", utbetalingshistorikk.utbetalteSykeperioder[11].typeKode)
        assertFalse(utbetalingshistorikk.statslønn)
    }

    @Test
    fun `utbetalingshistorikk med manglende fom og tom i utbetalingslisten`() {
        val json = readJson("infotrygdResponseMissingFomAndTom.json")
        val utbetalingshistorikk = Utbetalingshistorikk(json["sykmeldingsperioder"][1])

        assertNotNull(utbetalingshistorikk.inntektsopplysninger)
        assertNotNull(utbetalingshistorikk.utbetalteSykeperioder)
        assertEquals(12, utbetalingshistorikk.utbetalteSykeperioder.size)
        assertNull(utbetalingshistorikk.utbetalteSykeperioder[1].fom)
        assertNull(utbetalingshistorikk.utbetalteSykeperioder[2].tom)
        assertEquals("", utbetalingshistorikk.utbetalteSykeperioder[11].typeKode)
        assertFalse(utbetalingshistorikk.statslønn)
    }

    @Test
    fun `utbetalingshistorikk med ferie1Fom og ferie1Tom har med ferieperioden`() {
        val json = readJson("infotrygdResponseMedFerie1FomOgTom.json")
        val utbetalingshistorikk = Utbetalingshistorikk(json["sykmeldingsperioder"][0])

        assertNotNull(utbetalingshistorikk.inntektsopplysninger)
        assertNotNull(utbetalingshistorikk.utbetalteSykeperioder)
        assertEquals(3, utbetalingshistorikk.utbetalteSykeperioder.size)
        assertEquals("9", utbetalingshistorikk.utbetalteSykeperioder[2].typeKode)
        assertEquals("Ferie", utbetalingshistorikk.utbetalteSykeperioder[2].typeTekst)

        val utbetalingshistorikk2 = Utbetalingshistorikk(json["sykmeldingsperioder"][1])
        assertNotNull(utbetalingshistorikk2.inntektsopplysninger)
        assertNotNull(utbetalingshistorikk2.utbetalteSykeperioder)
        assertEquals(2, utbetalingshistorikk2.utbetalteSykeperioder.size)
        assertEquals("9", utbetalingshistorikk2.utbetalteSykeperioder[0].typeKode)
        assertEquals("Ferie", utbetalingshistorikk2.utbetalteSykeperioder[0].typeTekst)
        assertEquals("9", utbetalingshistorikk2.utbetalteSykeperioder[1].typeKode)
        assertEquals("Ferie", utbetalingshistorikk2.utbetalteSykeperioder[1].typeTekst)
    }

    @Test
    fun `parser hele lista med utbetalingshistorikk`() {
        val json = readJson("infotrygdResponse.json")
        val utbetalingshistorikkListe = json["sykmeldingsperioder"].map { Utbetalingshistorikk(it) }
        assertEquals(20, utbetalingshistorikkListe.size)
        assertEquals(13, utbetalingshistorikkListe.flatMap { it.inntektsopplysninger }.size)
    }

    private val objectMapper = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())

    fun readJson(fileName: String) = objectMapper.readTree(this::class.java.getResource("/$fileName").readText())
}
