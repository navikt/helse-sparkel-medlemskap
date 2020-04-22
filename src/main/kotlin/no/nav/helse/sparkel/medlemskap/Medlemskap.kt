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
            validate { it.require("medlemskapPeriodeFom", JsonNode::asLocalDate) }
            validate { it.require("medlemskapPeriodeTom", JsonNode::asLocalDate) }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        sikkerlogg.error("forstod ikke $behov:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val behovId = packet["@id"].asText()
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
        withMDC(mapOf(
            "behovId" to behovId,
            "vedtaksperiodeId" to vedtaksperiodeId
        )) {
            try {
                packet.info("løser behov {} for {}", keyValue("id", behovId), keyValue("vedtaksperiodeId", vedtaksperiodeId))
                håndter(packet, context)
            } catch (err: Exception) {
                packet.error("feil ved behov {} for {}: ${err.message}", keyValue("id", behovId), keyValue("vedtaksperiodeId", vedtaksperiodeId), err)

                packet["@løsning"] = mapOf<String, Any>(behov to emptyMap<String, Any>())
            }
        }
    }

    private fun håndter(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        packet["@løsning"] = mapOf<String, Any>(
            behov to client.hentMedlemskapsvurdering(
                fnr = packet["fødselsnummer"].asText(),
                fom = packet["medlemskapPeriodeFom"].asLocalDate(),
                tom = packet["medlemskapPeriodeTom"].asLocalDate(),
                arbeidUtenforNorge = false
            )
        )
        context.send(packet.toJson()).also {
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

    private fun JsonMessage.error(format: String, vararg args: Any) {
        log.error(format, *args)
        sikkerlogg.error(format, *args)
    }
}
