package no.nav.helse.sparkel.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal class Medlemskap(
    rapidsConnection: RapidsConnection,
    private val client: MedlemskapClient
) : River.PacketListener {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val log = LoggerFactory.getLogger(Medlemskap::class.java)
        private const val behov = "Medlemskap"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(behov)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
            validate { it.require("$behov.medlemskapPeriodeFom", JsonNode::asLocalDate) }
            validate { it.require("$behov.medlemskapPeriodeTom", JsonNode::asLocalDate) }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.error("forstod ikke $behov:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val behovId = packet["@id"].asText()
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
        withMDC(mapOf(
            "behovId" to behovId,
            "vedtaksperiodeId" to vedtaksperiodeId
        )) {
            try {
                packet.info("løser behov {} for {}", keyValue("id", behovId), keyValue("vedtaksperiodeId", vedtaksperiodeId))
                håndter(packet, context)
            } catch (err: MedlemskapException) {
                log.warn("feil ved behov {} for {}: ${err.message}", keyValue("id", behovId), keyValue("vedtaksperiodeId", vedtaksperiodeId), err)
                sikkerlogg.warn("feil ved behov {} for {}: ${err.message}\n\t${err.responseBody}", keyValue("id", behovId), keyValue("vedtaksperiodeId", vedtaksperiodeId), err)
                håndterFeil(packet, context)
            } catch (err: Exception) {
                packet.warn("feil ved behov {} for {}: ${err.message}", keyValue("id", behovId), keyValue("vedtaksperiodeId", vedtaksperiodeId), err)
                håndterFeil(packet, context)
            }
        }
    }

    private fun håndterFeil(packet: JsonMessage, context: MessageContext) {
        if ("dev-fss" != System.getenv("NAIS_CLUSTER_NAME")) return

        packet["@løsning"] = mapOf<String, Any>(behov to emptyMap<String, Any>())
        context.publish(packet.toJson()).also {
            sikkerlogg.info("sender {} som {}", keyValue("id", packet["@id"].asText()), packet.toJson())
        }
    }

    private fun håndter(packet: JsonMessage, context: MessageContext) {
        packet["@løsning"] = mapOf<String, Any>(
            behov to client.hentMedlemskapsvurdering(
                fnr = packet["fødselsnummer"].asText(),
                fom = packet["$behov.medlemskapPeriodeFom"].asLocalDate(),
                tom = packet["$behov.medlemskapPeriodeTom"].asLocalDate(),
                arbeidUtenforNorge = false
            )
        )
        context.publish(packet.toJson()).also {
            sikkerlogg.info("sender {} som {}", keyValue("id", packet["@id"].asText()), packet.toJson())
        }
    }

    private fun withMDC(context: Map<String, String>, block: () -> Unit) {
        val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
        try {
            MDC.setContextMap(contextMap + context)
            block()
        } finally {
            MDC.setContextMap(contextMap)
        }
    }

    private fun JsonMessage.info(format: String, vararg args: Any) {
        log.info(format, *args)
        sikkerlogg.info(format, *args)
    }

    private fun JsonMessage.warn(format: String, vararg args: Any) {
        log.warn(format, *args)
        sikkerlogg.warn(format, *args)
    }
}
