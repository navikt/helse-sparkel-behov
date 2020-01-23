package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.server.engine.ApplicationEngine
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import no.nav.helse.fixtures.januar
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppIntegrationTest {

    companion object {
        val orgnummer = "80000000"
    }

    private val port = ServerSocket(0).use { it.localPort }
    lateinit var app: ApplicationEngine
    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private val producer = KafkaProducer<String, String>(producerProperties(), StringSerializer(), StringSerializer())
    private val consumer =
        KafkaConsumer<String, String>(consumerProperties(), StringDeserializer(), StringDeserializer())


    @BeforeAll
    fun setup() = runBlocking {
        wireMockServer.start()
        embeddedKafkaEnvironment.start()
        app = launchApp(env(wireMockServer.baseUrl(), port))
        consumer.subscribe(listOf(rapidTopic))
        WireMock.configureFor(WireMock.create().port(wireMockServer.port()).build())
        stubEksterneEndepunkt()
    }

    @AfterAll
    internal fun `stop embedded environment`() {
        app.stop(1000, 1000, TimeUnit.MILLISECONDS)
        wireMockServer.stop()
        consumer.close()
        embeddedKafkaEnvironment.tearDown()
    }

    @Test
    internal fun `løser behov`() {

        val behov =
            """{"@id": "behovsid", "@behov":["$sykepengeperioderBehov"], "aktørId":"123", "$utgangspunktForBeregningAvYtelse": "2020-01-01", "fødselsnummer": "fnr" }"""

        var fantLøsning = false
        var id = 0
        while (id++ < 100 && !fantLøsning) {
            producer.send(ProducerRecord(rapidTopic, "key-$id", behov))
            await()
                .atMost(150, TimeUnit.MILLISECONDS)
                .untilAsserted {
                    consumer.poll(Duration.ofMillis(100))
                        ?.toList()
                        ?.map { objectMapper.readValue<JsonNode>(it.value()) }
                        ?.firstOrNull { it.hasNonNull("@løsning") }
                        ?.let {
                            assert(it.get("@løsning").hasNonNull(sykepengeperioderBehov))

                            val jsonNode: ArrayNode = it.get("@løsning").get(sykepengeperioderBehov) as ArrayNode

                            assertEquals(1, jsonNode.size())
                            assertEquals(3.januar, LocalDate.parse(jsonNode[0].get("fom").asText()))
                            assertEquals(23.januar, LocalDate.parse(jsonNode[0].get("tom").asText()))
                            assertEquals("100", jsonNode[0].get("grad").asText())
                            fantLøsning = true
                        }
                }
        }
        assert(fantLøsning)
    }

    @Test
    internal fun `mapper også ut inntekt og dagsats`() {

        val behov =
            """{"@id": "behovsid", "@behov":["$sykepengeperioderBehov"], "aktørId":"123", "$utgangspunktForBeregningAvYtelse": "2020-01-01", "fødselsnummer": "fnr" }"""

        var fantLøsning = false
        var id = 0
        while (id++ < 100 && !fantLøsning) {
            producer.send(ProducerRecord(rapidTopic, "key-$id", behov))
            await()
                .atMost(150, TimeUnit.MILLISECONDS)
                .untilAsserted {
                    consumer.poll(Duration.ofMillis(100))
                        ?.toList()
                        ?.map { objectMapper.readValue<JsonNode>(it.value()) }
                        ?.firstOrNull { it.hasNonNull("@løsning") }
                        ?.let {
                            assert(it.get("@løsning").hasNonNull(sykepengeperioderBehov))

                            val løsning: ArrayNode = it.get("@løsning").get(sykepengeperioderBehov) as ArrayNode

                            assertEquals(1, løsning.size())
                            assertEquals(3.januar, LocalDate.parse(løsning[0].get("fom").asText()))
                            assertEquals(23.januar, LocalDate.parse(løsning[0].get("tom").asText()))

                            val sykeperioder: ArrayNode = løsning[0].get("utbetalteSykeperioder") as ArrayNode
                            assertSykeperiode(
                                sykeperiode = sykeperioder[0],
                                fom = 19.januar,
                                tom = 23.januar,
                                grad = "100",
                                orgnummer = orgnummer,
                                inntektPerMåned = 18852,
                                dagsats = 870.0
                            )

                            val inntektsopplysninger = løsning[0].get("inntektsopplysninger") as ArrayNode
                            assertInntektsopplysninger(
                                inntektsopplysninger = inntektsopplysninger,
                                dato = 19.januar,
                                inntektPerMåned = 18852,
                                orgnummer = orgnummer
                            )
                            fantLøsning = true
                        }
                }
        }
        assert(fantLøsning)
    }

    private fun assertSykeperiode(
        sykeperiode: JsonNode,
        fom: LocalDate,
        tom: LocalDate,
        grad: String,
        orgnummer: String,
        inntektPerMåned: Int,
        dagsats: Double
    ) {
        assertEquals(fom, LocalDate.parse(sykeperiode.get("fom").asText()))
        assertEquals(tom, LocalDate.parse(sykeperiode.get("tom").asText()))
        assertEquals(grad, sykeperiode.get("utbetalingsGrad").asText())
        assertEquals(orgnummer, sykeperiode.get("orgnummer").asText())
        assertEquals(inntektPerMåned, sykeperiode.get("inntektPerMåned").intValue())
        assertEquals(dagsats, sykeperiode.get("dagsats").doubleValue())
    }

    private fun assertInntektsopplysninger(
        inntektsopplysninger: JsonNode,
        dato: LocalDate,
        inntektPerMåned: Int,
        orgnummer: String
    ) {
        assertEquals(dato, LocalDate.parse(inntektsopplysninger[0].get("sykepengerFom").asText()))
        assertEquals(inntektPerMåned, inntektsopplysninger[0].get("inntekt").asInt())
        assertEquals(orgnummer, inntektsopplysninger[0].get("orgnummer").asText())
    }

    fun env(wiremockUrl: String, port: Int) = mapOf(
        "HTTP_PORT" to "$port",
        "INFOTRYGD_SCOPE" to "INFOTRYGD_SCOPE",
        "AZURE_CLIENT_ID" to "AZURE_CLIENT_ID",
        "AZURE_CLIENT_SECRET" to "AZURE_CLIENT_SECRET",
        "AZURE_TENANT_ID" to "AZURE_TENANT_ID",
        "AZURE_TENANT_BASEURL" to wiremockUrl,
        "KAFKA_BOOTSTRAP_SERVERS" to embeddedKafkaEnvironment.brokersURL,
        "INFOTRYGD_URL" to wiremockUrl
    )

    private fun stubEksterneEndepunkt() {
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
        stubFor(
            get(urlPathEqualTo("/v1/hentSykepengerListe"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                                    {
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
                                              "arbOrgnr": 80000000
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
