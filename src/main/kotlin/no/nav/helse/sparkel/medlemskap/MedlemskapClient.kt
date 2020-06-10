package no.nav.helse.sparkel.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

internal class MedlemskapClient(
    private val baseUrl: String,
    private val azureClient: AzureClient,
    private val accesstokenScope: String = "??"
) {

    private companion object {
        private val objectMapper = ObjectMapper()
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
    }

    internal fun hentMedlemskapsvurdering(
        fnr: String,
        fom: LocalDate,
        tom: LocalDate,
        arbeidUtenforNorge: Boolean
    ): JsonNode {
        val (responseCode, responseBody) = with(URL(baseUrl).openConnection() as HttpURLConnection) {
            requestMethod = "POST"

            setRequestProperty("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
            outputStream.use {
                it.bufferedWriter().apply {
                    write("""{"fnr": "$fnr", "periode": {"fom": "$fom", "tom": "$tom" }, "brukerinput": { "arbeidUtenforNorge": ${ if (arbeidUtenforNorge) "true" else "false" } } }""")
                    flush()
                }
            }

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        tjenestekallLog.info("svar fra medlemskap: url=$baseUrl responseCode=$responseCode responseBody=$responseBody")

        if (responseCode >= 300 || responseBody == null) {
            throw MedlemskapException("unknown error (responseCode=$responseCode) from medlemskap", responseCode, responseBody)
        }

        return objectMapper.readTree(responseBody)
    }
}

internal class MedlemskapException(message: String, val statusCode: Int, val responseBody: String?) : RuntimeException(message)

