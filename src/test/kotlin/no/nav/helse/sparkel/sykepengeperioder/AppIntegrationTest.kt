package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
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
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import java.time.Duration
import java.util.HashMap
import java.util.Properties
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppIntegrationTest {

    companion object {
        val port = ServerSocket(0).use { it.localPort }
        lateinit var app: ApplicationEngine
        private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    }


    private fun producerProperties() =
        Properties().apply {
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
            put(ProducerConfig.LINGER_MS_CONFIG, "0")
            put(ProducerConfig.RETRIES_CONFIG, "0")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        }

    private fun consumerProperties(): MutableMap<String, Any>? {
        return HashMap<String, Any>().apply {
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(ConsumerConfig.GROUP_ID_CONFIG, "end-to-end-test")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        }
    }

    private val topicInfos = listOf(
        KafkaEnvironment.TopicInfo(behovTopic)
    )

    private val embeddedKafkaEnvironment = KafkaEnvironment(
        autoStart = false,
        noOfBrokers = 1,
        topicInfos = topicInfos,
        withSchemaRegistry = false,
        withSecurity = false
    )


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

    private val producer = KafkaProducer<String, String>(producerProperties(), StringSerializer(), StringSerializer())
    private val consumer =
        KafkaConsumer<String, String>(consumerProperties(), StringDeserializer(), StringDeserializer())


    @BeforeAll
    fun setup() = runBlocking {
        wireMockServer.start()
        embeddedKafkaEnvironment.start()
        app = launchApp(env(wireMockServer.baseUrl(), port))
        consumer.subscribe(listOf(behovTopic))
        val client = WireMock.create().port(wireMockServer.port()).build()
        WireMock.configureFor(client)
        stubFor(
            post(urlMatching("/AZURE_TENANT_ID/oauth2/v2.0/token"))
//                .withHeader("Accept", equalTo("application/json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                    "token_type": "Bearer",
                    "expires_in": 3599,
                    "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP..."
                }"""
                        )
                )

        )
        stubFor(
            get(urlPathEqualTo("/hentSykepengerListe"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"sykmeldingsperioder": []}"""
                        )
                )
        )
        println("log")
    }

    @AfterAll
    internal fun `stop embedded environment`() {
        app.stop(1000, 1000, TimeUnit.MILLISECONDS)
        wireMockServer.stop()
        consumer.close()
        embeddedKafkaEnvironment.tearDown()
    }

    @Test
    internal fun test() {

        val behov =
            """{"@id": "behovsid", "@behov":["$sykepengeperioderBehov"], "aktørId":"123", "$utgangspunktForBeregningAvYtelse": "2020-01-01"}"""

        var fantLøsning = false
        var id = 0
        while (id < 30) {
            producer.send(ProducerRecord(behovTopic, "key-$id", behov))
            await()
                .atMost(150, TimeUnit.MILLISECONDS)
                .untilAsserted {
                    consumer.poll(Duration.ofMillis(100))
                        ?.toList()
                        ?.map { objectMapper.readValue<JsonNode>(it.value()) }
                        ?.filter { it.hasNonNull("@løsning") }
                        ?.firstOrNull()
                        ?.let {
                            assert(it.get("@løsning").hasNonNull(sykepengeperioderBehov)
                                .also { fantLøsning = true })
                        }
                }
            id += 1
        }
        assert(fantLøsning)
    }

    private fun assertLøsning(duration: Duration, assertion: (List<JsonNode>) -> Unit) =
        mutableListOf<ConsumerRecord<String, String>>().apply {
            await()
                .atMost(duration)
                .untilAsserted {
                    addAll(consumer.poll(Duration.ofMillis(100)).toList())
                    assertion(map { objectMapper.readValue<JsonNode>(it.value()) }.filter { it.hasNonNull("@løsning") })
                }
        }
}