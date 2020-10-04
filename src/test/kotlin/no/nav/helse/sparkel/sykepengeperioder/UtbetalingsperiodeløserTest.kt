package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.AzureClient
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.InfotrygdClient
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class UtbetalingsperiodeløserTest {

    private companion object {
        private const val orgnummer = "80000000"
    }

    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    private var sendtMelding: JsonNode = objectMapper.createObjectNode()
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
        WireMock.configureFor(WireMock.create().port(wireMockServer.port()).build())
        stubEksterneEndepunkt()
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

    @BeforeEach
    internal fun beforeEach() {
        sendtMelding = objectMapper.createObjectNode()
    }

    @Test
    fun `løser enkelt behov`() {
        testBehov(enkeltBehov())

        val perioder = sendtMelding.løsning()

        assertEquals(1, perioder.size)
    }

    @Test
    fun `løser behov med flere behov-nøkler`() {
        testBehov(behovMedFlereBehovsnøkler())

        val perioder = sendtMelding.løsning()
        println(sendtMelding.toPrettyString())

        assertEquals(1, perioder.size)
    }

    @Test
    fun `River svarer på behov`() {
        testBehov(behovMedFlereBehovsnøkler())
        assertFalse(sendtMelding.isEmpty)
    }

    @Test
    fun `mapper også ut perioder`() {
        testBehov(enkeltBehov())

        val løsninger = sendtMelding.løsning()
        assertEquals(1, løsninger.size)

        val periode = løsninger.first()

        assertInfotrygdperiode(
            periode = periode,
            fom = 19.januar,
            tom = 23.januar,
            grad = "100",
            dagsats = 870.0,
            typetekst = "ArbRef",
            organisasjonsnummer = orgnummer
        )
    }

    private fun JsonNode.løsning() = this.path("@løsning").path(Utbetalingsperiodeløser.behov).map {
        Infotrygdperiode(it)
    }

    class Infotrygdperiode(json: JsonNode) {
        val fom = json["fom"].asLocalDate()
        val tom = json["tom"].asLocalDate()
        val grad = json["grad"].asText()
        val dagsats = json["dagsats"].asDouble()
        val typetekst = json["typetekst"].asText()
        val organisasjonsnummer = json["organisasjonsnummer"].asText()

        private companion object {
            fun JsonNode.asLocalDate() = LocalDate.parse(this.asText())
        }
    }

    private fun testBehov(behov: String) {
        Utbetalingsperiodeløser(rapid, service)
        rapid.sendTestMessage(behov)
    }

    private fun assertInfotrygdperiode(
        periode: Infotrygdperiode,
        fom: LocalDate,
        tom: LocalDate, grad: String,
        dagsats: Double,
        typetekst: String,
        organisasjonsnummer: String
    ) {
        assertEquals(fom, periode.fom)
        assertEquals(tom, periode.tom)
        assertEquals(grad, periode.grad)
        assertEquals(dagsats, periode.dagsats)
        assertEquals(typetekst, periode.typetekst)
        assertEquals(organisasjonsnummer, periode.organisasjonsnummer)
    }

    private fun behovMedFlereBehovsnøkler() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "HentEnhet", "HentPersoninfo", "HentInfotrygdutbetalinger" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "fnr",
            "orgnummer" : "orgnr",
            "HentInfotrygdutbetalinger": {
                "historikkFom" : "2017-05-18",
                "historikkTom" : "2020-05-18"
            }
        }
        """.trimIndent()

    private fun enkeltBehov() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "HentInfotrygdutbetalinger" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "fnr",
            "orgnummer" : "orgnr",
            "HentInfotrygdutbetalinger": {
                "historikkFom" : "2017-05-18",
                "historikkTom" : "2020-05-18"
            }
        }
        """.trimIndent()

    private fun stubEksterneEndepunkt() {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/AZURE_TENANT_ID/oauth2/v2.0/token"))
                .willReturn(
                    WireMock.aResponse()
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
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/v1/hentSykepengerListe"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                                      "sykmeldingsperioder": [
                                        {
                                          "ident": 1000,
                                          "tknr": "0220",
                                          "seq": 79999596,
                                          "sykemeldtFom": "2018-01-03",
                                          "sykemeldtTom": "2018-01-23",
                                          "grad": "100",
                                          "slutt": "2019-03-30",
                                          "erArbeidsgiverPeriode": true,
                                          "stansAarsakKode": "AF",
                                          "stansAarsak": "AvsluttetFrisk",
                                          "unntakAktivitet": "",
                                          "arbeidsKategoriKode": "01",
                                          "arbeidsKategori": "Arbeidstaker",
                                          "arbeidsKategori99": "",
                                          "erSanksjonBekreftet": "",
                                          "sanksjonsDager": 0,
                                          "sykemelder": "NØDNUMMER",
                                          "behandlet": "2018-01-05",
                                          "yrkesskadeArt": "",
                                          "utbetalingList": [
                                            {
                                              "fom": "2018-01-19",
                                              "tom": "2018-01-23",
                                              "utbetalingsGrad": "100",
                                              "oppgjorsType": "50",
                                              "utbetalt": "2018-02-16",
                                              "dagsats": 870.0,
                                              "typeKode": "5",
                                              "typeTekst": "ArbRef",
                                              "arbOrgnr": $orgnummer
                                            }
                                          ],
                                          "inntektList": [
                                            {
                                              "orgNr": "80000000",
                                              "sykepengerFom": "2018-01-19",
                                              "refusjonTom": "2018-01-30",
                                              "refusjonsType": "H",
                                              "periodeKode": "U",
                                              "periode": "Ukentlig",
                                              "loenn": 4350.5
                                            }
                                          ],
                                          "graderingList": [
                                            {
                                              "gradertFom": "2018-01-03",
                                              "gradertTom": "2018-01-23",
                                              "grad": "100"
                                            }
                                          ],
                                          "forsikring": []
                                        }
                                      ]
                                    }"""
                        )
                )
        )
    }
}