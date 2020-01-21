package no.nav.helse.sparkel.sykepengeperioder

import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import java.util.HashMap
import java.util.Properties

internal val topicInfos = listOf(
    KafkaEnvironment.TopicInfo(behovTopic)
)

internal val embeddedKafkaEnvironment = KafkaEnvironment(
    autoStart = false,
    noOfBrokers = 1,
    topicInfos = topicInfos,
    withSchemaRegistry = false,
    withSecurity = false
)

internal fun producerProperties() =
    Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.RETRIES_CONFIG, "0")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
    }

internal fun consumerProperties(): MutableMap<String, Any>? {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(ConsumerConfig.GROUP_ID_CONFIG, "end-to-end-test")
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
}