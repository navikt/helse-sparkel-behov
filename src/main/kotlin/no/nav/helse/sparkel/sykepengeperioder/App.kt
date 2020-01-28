package no.nav.helse.sparkel.sykepengeperioder

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.helse.rapids_rivers.AppBuilder
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.AzureClient
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.InfotrygdClient
import java.io.File
import java.io.FileNotFoundException

fun main() {
    val app = createApp(System.getenv())
    GlobalScope.launch {
        app.start()
    }
}

fun createApp(env: Map<String, String>): AppBuilder {
    val builder = AppBuilder(env)

    val azureClient = AzureClient(
            tenantUrl = "${env.getValue("AZURE_TENANT_BASEURL")}/${env.getValue("AZURE_TENANT_ID")}",
            clientId = "/var/run/secrets/nais.io/azure/client_id".readFile() ?: env.getValue("AZURE_CLIENT_ID"),
            clientSecret = "/var/run/secrets/nais.io/azure/client_secret".readFile() ?: env.getValue("AZURE_CLIENT_SECRET")
    )
    val infotrygdClient = InfotrygdClient(
            baseUrl = env.getValue("INFOTRYGD_URL"),
            accesstokenScope = env.getValue("INFOTRYGD_SCOPE"),
            azureClient = azureClient
    )

    val river = SykepengehistorikkRiver()
    river.register(Sykepengehistorikkløser(infotrygdClient))
    builder.register(river)

    return builder
}

private fun String.readFile() =
        try {
            File(this).readText(Charsets.UTF_8)
        } catch (err: FileNotFoundException) {
            null
        }
