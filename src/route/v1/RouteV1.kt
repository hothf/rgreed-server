package route.v1

import de.ka.rgreed.route.v1.*
import io.ktor.routing.*

/**
 * This file represents a kotlin extension function for all route calls.
 *
 * It shows all the available controller for consuming the api with the different actions.
 */
fun Route.routesV1() {

    route("consensus") {
        routeConsensusV1()
    }

    route("consensus/{consensusId}/suggestions") {
        routeConsensusSuggestionsV1()
    }

    route("login") {
        routeLoginV1()
    }

    route("register") {
        routeRegisterLoginV1()
    }
}