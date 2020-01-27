package no.nav.helse.sparkel.sykepengeperioder

import io.ktor.config.ApplicationConfig
import io.ktor.config.MapApplicationConfig
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.sparkel.sykepengeperioder.nais.nais
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
fun createConfigFromEnvironment(env: Map<String, String>) =
    MapApplicationConfig().apply {
        put("server.port", env.getOrDefault("HTTP_PORT", "8080"))

        put("kafka.app-id", "sparkel-sykepengeperioder-v2")

        env["KAFKA_BOOTSTRAP_SERVERS"]?.let { put("kafka.bootstrap-servers", it) }
        "/var/run/secrets/nais.io/service_user/username".readFile()?.let { put("kafka.username", it) }
        "/var/run/secrets/nais.io/service_user/password".readFile()?.let { put("kafka.password", it) }

        env["NAV_TRUSTSTORE_PATH"]?.let { put("kafka.truststore-path", it) }
        env["NAV_TRUSTSTORE_PASSWORD"]?.let { put("kafka.truststore-password", it) }

        put("infotrygd.url", env.getValue("INFOTRYGD_URL"))
        put("infotrygd.scope", env.getValue("INFOTRYGD_SCOPE"))
        put("azure.tenant.url", "${env["AZURE_TENANT_BASEURL"]}/${env.getValue("AZURE_TENANT_ID")}")
        put("azure.tenant_id", env.getValue("AZURE_TENANT_ID"))
        put("azure.client_id", "/var/run/secrets/nais.io/azure/client_id".readFile() ?: env.getValue("AZURE_CLIENT_ID"))
        put(
            "azure.client_secret",
            "/var/run/secrets/nais.io/azure/client_secret".readFile() ?: env.getValue("AZURE_CLIENT_SECRET")
        )
    }

private fun String.readFile() =
    try {
        File(this).readText(Charsets.UTF_8)
    } catch (err: FileNotFoundException) {
        null
    }

@KtorExperimentalAPI
fun main() {
    launchApp(System.getenv())
}

@KtorExperimentalAPI
fun launchApp(env: Map<String, String>): ApplicationEngine {
    val config = createConfigFromEnvironment(env)
    return embeddedServer(Netty, createApplicationEnvironment(config)).also { app ->
        app.start(wait = false)

        Runtime.getRuntime().addShutdownHook(Thread {
            app.stop(1, 1, TimeUnit.SECONDS)
        })
    }
}

@KtorExperimentalAPI
fun createApplicationEnvironment(appConfig: ApplicationConfig) = applicationEngineEnvironment {
    config = appConfig

    connector {
        port = appConfig.property("server.port").getString().toInt()
    }

    module {
        val streams = sykepengeperioderApplication()
        nais(
            isAliveCheck = { streams.state().isRunning }
        )
    }
}
