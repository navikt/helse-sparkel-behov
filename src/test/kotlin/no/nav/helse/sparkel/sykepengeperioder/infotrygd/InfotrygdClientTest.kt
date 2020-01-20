package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class InfotrygdClientTest {

    companion object {
        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
        }
    }

    private val infotrygdClient: InfotrygdClient

    init {
        val azureClientMock = mockk<AzureClient>()
        every {
            azureClientMock.getToken("scope")
        } returns AzureClient.Token(
                tokenType = "Bearer",
                expiresIn = 3600,
                accessToken = "foobar"
        )

        infotrygdClient = InfotrygdClient(baseUrl = server.baseUrl(), accesstokenScope = "scope", azureClient = azureClientMock)
    }

    @BeforeEach
    fun configure() {
        WireMock.configureFor(server.port())
    }

    @Test
    fun `henter sykepengeperioder fra infotrygd`() {
        stubFor(infotrygdRequestMapping
                .willReturn(WireMock.ok(ok_sykepengeperioder_response)))

        val sykepengeperioder = infotrygdClient.hentHistorikk(fnr = fnr, datoForYtelse = datoForYtelse)

        assertEquals(1, sykepengeperioder.size)
        assertEquals(LocalDate.parse("2000-04-03"), sykepengeperioder[0].fom)
        assertEquals(LocalDate.parse("2000-04-23"), sykepengeperioder[0].tom)
        assertEquals("100", sykepengeperioder[0].grad)
    }
}

private val fnr = "123456789123"
private val datoForYtelse = LocalDate.of(2019, 1, 1)

private val infotrygdRequestMapping = get(urlPathEqualTo("/hentSykepengerListe"))
        .withQueryParam("fnr", equalTo(fnr))
        .withQueryParam("fraDato", equalTo(datoForYtelse.minusYears(3).toString()))
        .withQueryParam("tilDato", equalTo(datoForYtelse.toString()))
        .withHeader("Authorization", equalTo("Bearer foobar"))
        .withHeader("Accept", equalTo("application/json"))

private val ok_sykepengeperioder_response = """
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
}""".trimIndent()

