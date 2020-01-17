package no.nav.helse.spleis

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get

internal fun Route.utbetaling(personRestInterface: PersonRestInterface) {
    get("/api/utbetaling/{utbetalingsreferanse}") {
        personRestInterface.hentSakForUtbetaling(call.parameters["utbetalingsreferanse"]!!)?.let { call.respond(it.memento().state()) }
                ?: call.respond(HttpStatusCode.NotFound, "Resource not found")
    }
}
