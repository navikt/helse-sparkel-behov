package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class InfotrygdClient(private val baseUrl: String, private val accesstokenScope: String, private val azureClient: AzureClient) {

    companion object {
        private val objectMapper = ObjectMapper()
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
    }

    fun hentHistorikk(fnr: String, datoForYtelse: LocalDate): List<Periode> {
        val historikkFom = datoForYtelse.minusYears(3)
        val url = "${baseUrl}/hentSykepengerListe?fnr=$fnr&fraDato=${historikkFom.format(DateTimeFormatter.ISO_DATE)}&tilDato=${datoForYtelse.format(DateTimeFormatter.ISO_DATE)}"
        val (responseCode, responseBody) = with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = "GET"

            setRequestProperty("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
            setRequestProperty("Accept", "application/json")

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        tjenestekallLog.info("svar fra Infotrygd: url=$url responseCode=$responseCode responseBody=$responseBody")

        if (responseCode >= 300 || responseBody == null) {
            throw RuntimeException("unknown error (responseCode=$responseCode) from Infotrygd")
        }

        val jsonNode = objectMapper.readTree(responseBody)

        return (jsonNode["sykmeldingsperioder"] as ArrayNode).map { periodeJson ->
            Periode(
                    fom = LocalDate.parse(periodeJson["sykemeldtFom"].textValue()),
                    tom = LocalDate.parse(periodeJson["sykemeldtTom"].textValue()),
                    grad = periodeJson["grad"].textValue()
            )
        }
    }
}

data class Periode(val fom: LocalDate, val tom: LocalDate, val grad: String)
