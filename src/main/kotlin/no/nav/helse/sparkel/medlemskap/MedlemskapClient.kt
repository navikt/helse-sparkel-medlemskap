package no.nav.helse.sparkel.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class MedlemskapClient(
    private val baseUrl: String,
    private val azureClient: AzureClient,
    private val accesstokenScope: String = "??"
) {

    companion object {
        private val objectMapper = ObjectMapper()
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
    }

    internal fun hentMedlemskapsvurdering(
        fnr: String,
        fom: LocalDate,
        tom: LocalDate
    ): JsonNode {
        val (responseCode, responseBody) = with(URL(baseUrl).openConnection() as HttpURLConnection) {
            requestMethod = "POST"

            setRequestProperty("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.bufferedWriter().apply {
                write("""{"fnr": "$fnr", "periode": {"fom": "$fom", "tom": "$tom" } }""")
                flush()
            }

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        tjenestekallLog.info("svar fra medlemskap: url=$baseUrl responseCode=$responseCode responseBody=$responseBody")

        if (responseCode >= 300 || responseBody == null) {
            throw RuntimeException("unknown error (responseCode=$responseCode) from medlemskap")
        }

        return objectMapper.readTree(responseBody)
    }
}

