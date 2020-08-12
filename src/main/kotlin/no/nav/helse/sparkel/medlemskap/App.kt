package no.nav.helse.sparkel.medlemskap

import no.nav.helse.rapids_rivers.RapidApplication
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    val env = System.getenv()
    RapidApplication.create(env).apply {
        val client = MedlemskapClient("http://medlemskap-oppslag.medlemskap.svc.nais.local", AzureClient(
            tenantUrl = "${env.getValue("AZURE_TENANT_BASEURL")}/${env.getValue("AZURE_TENANT_ID")}",
            clientId = "/var/run/secrets/nais.io/azuread/client_id".readFile() ?: env.getValue("AZURE_CLIENT_ID"),
            clientSecret = "/var/run/secrets/nais.io/azuread/client_secret".readFile() ?: env.getValue("AZURE_CLIENT_SECRET")
        ), env.getValue("MEDLEMSKAP_SCOPE"))
        Medlemskap(this, client)
    }.start()
}

private fun String.readFile() = Files.readString(Paths.get(this))

