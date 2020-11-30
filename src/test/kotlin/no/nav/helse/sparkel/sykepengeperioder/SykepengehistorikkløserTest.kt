package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.create
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.AzureClient
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.InfotrygdClient
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance.Lifecycle
import java.time.LocalDate

@TestInstance(Lifecycle.PER_CLASS)
internal class SykepengehistorikkløserTest {

    private companion object {
        private const val orgnummer = "80000000"
    }

    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    private lateinit var sendtMelding: JsonNode
    private lateinit var service: InfotrygdService

    private val context = object : RapidsConnection.MessageContext {
        override fun send(message: String) {
            sendtMelding = objectMapper.readTree(message)
        }

        override fun send(key: String, message: String) {}
    }

    private val rapid = object : RapidsConnection() {

        fun sendTestMessage(message: String) {
            listeners.forEach { it.onMessage(message, context) }
        }

        override fun publish(message: String) {}

        override fun publish(key: String, message: String) {}

        override fun start() {}

        override fun stop() {}
    }

    @BeforeAll
    fun setup() {
        wireMockServer.start()
        configureFor(create().port(wireMockServer.port()).build())
        stubAuthEndepunkt()
        service = InfotrygdService(
            InfotrygdClient(
                baseUrl = wireMockServer.baseUrl(),
                accesstokenScope = "a_scope",
                azureClient = AzureClient(
                    tenantUrl = "${wireMockServer.baseUrl()}/AZURE_TENANT_ID",
                    clientId = "client_id",
                    clientSecret = "client_secret"
                )
            )
        )
    }

    @AfterAll
    internal fun teardown() {
        wireMockServer.stop()
    }

    @Test
    fun `løser behov`() {
        stubSvarFraInfotrygd()

        val behov =
            """{"@id": "behovsid", "@behov":["${Sykepengehistorikkløser.behov}"], "${Sykepengehistorikkløser.behov}": { "historikkFom": "2016-01-01", "historikkTom": "2020-01-01"}, "fødselsnummer": "fnr", "vedtaksperiodeId": "id" }"""

        testBehov(behov)

        val perioder = sendtMelding.løsning()

        assertEquals(2, perioder.size)
    }

    @Test
    fun `løser behov uten vedtaksperiodeId`() {
        stubSvarFraInfotrygd()

        val behov =
            """{"@id": "behovsid", "@behov":["${Sykepengehistorikkløser.behov}"], "${Sykepengehistorikkløser.behov}": { "historikkFom": "2016-01-01", "historikkTom": "2020-01-01"}, "fødselsnummer": "fnr" }"""

        testBehov(behov)

        val perioder = sendtMelding.løsning()
        assertEquals(2, perioder.size)
    }

    @Test
    internal fun `mapper også ut inntekt og dagsats`() {
        stubSvarFraInfotrygd()

        val behov =
            """{"@id": "behovsid", "@behov":["${Sykepengehistorikkløser.behov}"], "${Sykepengehistorikkløser.behov}": { "historikkFom": "2016-01-01", "historikkTom": "2020-01-01" }, "fødselsnummer": "fnr", "vedtaksperiodeId": "id" }"""

        testBehov(behov)

        val perioder = sendtMelding.løsning()

        assertEquals(2, perioder.size)

        assertSykeperiode(
            sykeperiode = perioder[0].utbetalteSykeperioder[0],
            fom = 5.september(2020),
            tom = 25.september(2020),
            grad = "100",
            orgnummer = orgnummer,
            dagsats = 2176.0
        )

        assertInntektsopplysninger(
            inntektsopplysninger = perioder[0].inntektsopplysninger,
            dato = 4.september(2020),
            inntektPerMåned = 565700 / 12,
            orgnummer = orgnummer
        )
        assertInntektsopplysninger(
            inntektsopplysninger = perioder[1].inntektsopplysninger,
            dato = 4.februar(2019),
            inntektPerMåned = 507680 / 12,
            orgnummer = orgnummer
        )
    }

    @Test
    fun `setter ikke statslønn hvis tidligere periode har statslønn`() {
        stubSvarFraInfotrygd()
        val behov =
                """{"@id": "behovsid", "@behov":["${Sykepengehistorikkløser.behov}"], "${Sykepengehistorikkløser.behov}": { "historikkFom": "2016-01-01", "historikkTom": "2020-01-01"}, "fødselsnummer": "fnr", "vedtaksperiodeId": "id" }"""

        testBehov(behov)
        sendtMelding.løsning().let { utenStatslønn ->
            utenStatslønn.forEach { periode ->
                assertFalse(periode.statslønn)
            }
        }
    }

    @Test
    fun `setter statslønn hvis nyeste periode har statslønn`() {
        stubSvarFraInfotrygdMedAktivStatslønn()
        val behov =
                """{"@id": "behovsid", "@behov":["${Sykepengehistorikkløser.behov}"], "${Sykepengehistorikkløser.behov}": { "historikkFom": "2016-01-01", "historikkTom": "2020-01-01" }, "fødselsnummer": "fnr", "vedtaksperiodeId": "id" }"""

        testBehov(behov)
        sendtMelding.løsning().let { medStatlønn ->
            assertTrue(medStatlønn[0].statslønn)
            assertFalse(medStatlønn[1].statslønn)
        }
    }

    private fun JsonNode.løsning() = this.path("@løsning").path(Sykepengehistorikkløser.behov).map {
        Utbetalingshistorikk(it)
    }

    private class Utbetalingshistorikk(json: JsonNode) {

        val utbetalteSykeperioder = json["utbetalteSykeperioder"].map {
            UtbetalteSykeperiode(it)
        }
        val inntektsopplysninger = json["inntektsopplysninger"].map {
            Inntektsopplysning(it)
        }
        val statslønn = json["statslønn"].asBoolean()

        class UtbetalteSykeperiode(json: JsonNode) {
            val fom = json["fom"].asLocalDate()
            val tom = json["tom"].asLocalDate()
            val utbetalingsGrad = json["utbetalingsGrad"].asText()
            val orgnummer = json["orgnummer"].asText()
            val dagsats = json["dagsats"].asDouble()
        }

        class Inntektsopplysning(json: JsonNode) {
            val sykepengerFom = json["sykepengerFom"].asLocalDate()
            val inntekt = json["inntekt"].asInt()
            val orgnummer = json["orgnummer"].asText()
        }

        private companion object {
            fun JsonNode.asLocalDate() = LocalDate.parse(this.asText())
        }
    }

    private fun testBehov(behov: String) {
        Sykepengehistorikkløser(rapid, service)
        rapid.sendTestMessage(behov)
    }

    private fun assertSykeperiode(
        sykeperiode: Utbetalingshistorikk.UtbetalteSykeperiode,
        fom: LocalDate,
        tom: LocalDate,
        grad: String,
        orgnummer: String,
        dagsats: Double
    ) {
        assertEquals(fom, sykeperiode.fom)
        assertEquals(tom, sykeperiode.tom)
        assertEquals(grad, sykeperiode.utbetalingsGrad)
        assertEquals(orgnummer, sykeperiode.orgnummer)
        assertEquals(dagsats, sykeperiode.dagsats)
    }

    private fun assertInntektsopplysninger(
        inntektsopplysninger: List<Utbetalingshistorikk.Inntektsopplysning>,
        dato: LocalDate,
        inntektPerMåned: Int,
        orgnummer: String
    ) {
        assertEquals(dato, inntektsopplysninger[0].sykepengerFom)
        assertEquals(inntektPerMåned, inntektsopplysninger[0].inntekt)
        assertEquals(orgnummer, inntektsopplysninger[0].orgnummer)
    }

    private fun stubAuthEndepunkt() {
        stubFor(
            post(urlMatching("/AZURE_TENANT_ID/oauth2/v2.0/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                        "token_type": "Bearer",
                        "expires_in": 3599,
                        "access_token": "1234abc"
                    }"""
                        )
                )
        )
    }

    private fun stubSvarFraInfotrygdMedAktivStatslønn() {
        stubFor(
                get(urlPathEqualTo("/v1/hentSykepengerListe"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                getContents("statslønnAktivResponse.json")
                                        )
                        )
        )
    }

    private fun stubSvarFraInfotrygd() {
        stubFor(
                get(urlPathEqualTo("/v1/hentSykepengerListe"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(getContents("statslønnTidligereResponse.json"))
                        )
        )
    }

    private fun getContents(filename: String) = this::class.java.getResource("/$filename").readText()
}
