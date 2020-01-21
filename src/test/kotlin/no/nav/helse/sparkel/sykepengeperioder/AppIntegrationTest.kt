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
        consumer.subscribe(listOf(behovTopic))
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
            """{"@id": "behovsid", "@behov":["$sykepengeperioderBehov"], "aktørId":"123", "$utgangspunktForBeregningAvYtelse": "2020-01-01"}"""

        var fantLøsning = false
        var id = 0
        while (id++ < 100 && !fantLøsning) {
            producer.send(ProducerRecord(behovTopic, "key-$id", behov))
            await()
                .atMost(150, TimeUnit.MILLISECONDS)
                .untilAsserted {
                    consumer.poll(Duration.ofMillis(100))
                        ?.toList()
                        ?.map { objectMapper.readValue<JsonNode>(it.value()) }
                        ?.firstOrNull { it.hasNonNull("@løsning") }
                        ?.let {
                            assert(it.get("@løsning").hasNonNull(sykepengeperioderBehov)).also { fantLøsning = true }

                            val jsonNode: ArrayNode = it.get("@løsning").get(sykepengeperioderBehov) as ArrayNode

                            assertEquals(1, jsonNode.size())
                            assertEquals(LocalDate.parse("2000-04-03"), jsonNode[0].get("fom"))
                            assertEquals(LocalDate.parse("2000-04-23"), jsonNode[0].get("tom"))
                            assertEquals("100", jsonNode[0].get("grad"))
                        }
                }
        }
        assert(fantLøsning)
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
                                          "sykemeldtFom": "2000-04-03",
                                          "sykemeldtTom": "2000-04-23",
                                          "grad": "100",
                                          "slutt": "2001-03-30",
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
                                          "behandlet": "2000-04-05",
                                          "yrkesskadeArt": "",
                                          "utbetalingList": [
                                            {
                                              "fom": "2000-04-19",
                                              "tom": "2000-04-23",
                                              "utbetalingsGrad": "100",
                                              "oppgjorsType": "50",
                                              "utbetalt": "2000-05-16",
                                              "dagsats": 870.0,
                                              "typeKode": "5",
                                              "typeTekst": "ArbRef",
                                              "arbOrgnr": 80000000
                                            }
                                          ],
                                          "inntektList": [
                                            {
                                              "orgNr": "80000000",
                                              "sykepengerFom": "2000-04-19",
                                              "refusjonTom": "2000-04-30",
                                              "refusjonsType": "H",
                                              "periodeKode": "U",
                                              "periode": "Ukentlig",
                                              "loenn": 4350.5
                                            }
                                          ],
                                          "graderingList": [
                                            {
                                              "gradertFom": "2000-04-03",
                                              "gradertTom": "2000-04-23",
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