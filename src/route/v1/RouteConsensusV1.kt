package de.ka.rgreed.route.v1

import de.ka.rgreed.dao.ConsensusRepository
import de.ka.rgreed.dao.ConsensusRepository.getConsensusOrThrow
import de.ka.rgreed.dao.ConsensusRepository.analyzeConsensusBodyAndThrow
import de.ka.rgreed.model.*
import de.ka.rgreed.util.getAuthUser
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*


/**
 * This file represents a kotlin extension function for routes v1 calls concerning the [ConsensusRepository].
 */
fun Route.routeConsensusV1() {

    authenticate(optional = true) {
        get("/") {
            val limit = call.parameters["limit"]?.toInt() ?: 100
            val offset = call.parameters["offset"]?.toInt() ?: 0
            val filter = call.parameters["finished"]?.toBoolean()
            val searchQuery = call.parameters["search"]

            getAuthUser().let {
                if (searchQuery != null) {
                    call.respond(ConsensusRepository.getSearchQueryResponse(searchQuery.trim().toLowerCase(), it, limit, offset))
                } else {
                    call.respond(ConsensusRepository.getAllResponse(it, limit, offset, filter))
                }
            }
        }

        authenticate {
            get("/admin") {
                val limit = call.parameters["limit"]?.toInt() ?: 100
                val offset = call.parameters["offset"]?.toInt() ?: 0
                val filter = call.parameters["finished"]?.toBoolean()

                getAuthUser()?.let {
                    call.respond(ConsensusRepository.getConsensusAdminResponse(it, limit, offset, filter))
                }
            }
            get("/following") {
                val limit = call.parameters["limit"]?.toInt() ?: 100
                val offset = call.parameters["offset"]?.toInt() ?: 0
                val filter = call.parameters["finished"]?.toBoolean()

                getAuthUser()?.let {
                    call.respond(ConsensusRepository.getConsensusFollowedResponse(it, limit, offset, filter))
                }
            }
        }

        get("/{id}") {
            val storedConsensus = getConsensusOrThrow("id")

            getAuthUser().let {
                call.respond(ConsensusRepository.getResponse(storedConsensus, it))
            }
        }
    }

    authenticate {
        post("/") {
            val consensusBody = call.receive<ConsensusBody>().analyzeConsensusBodyAndThrow()

            getAuthUser()?.let {
                call.respond(HttpStatusCode.Created, ConsensusRepository.createResponse(consensusBody, it))
            }
        }

        put("/{id}") {
            val storedConsensus = getConsensusOrThrow("id")
            val consensusBody = call.receive<ConsensusBody>().analyzeConsensusBodyAndThrow()

            when {
                storedConsensus.isFinished -> {
                    throw ConsensusException(HttpStatusCode.BadRequest, listOf(Error(ErrorType.BAD_REQUEST)))
                }
                else -> getAuthUser()?.let {
                    if (!ConsensusRepository.isAdmin(storedConsensus, it)) {
                        throw ConsensusException(HttpStatusCode.Forbidden, listOf(Error(ErrorType.FORBIDDEN)))
                    }
                    call.respond(ConsensusRepository.updateResponse(storedConsensus, consensusBody, it))
                }
            }
        }

        post("/{id}/requestAccess") {
            val storedConsensus = getConsensusOrThrow("id")
            val requestAccessBody = call.receive<RequestAccessBody>()

            getAuthUser()?.let {
                call.respond(ConsensusRepository.requestAccessResponse(storedConsensus, requestAccessBody, it))
            }
        }

        post("/{id}/follow") {
            val storedConsensus = getConsensusOrThrow("id")
            val followBody = call.receive<FollowBody>()

            getAuthUser()?.let {
                call.respond(ConsensusRepository.followResponse(storedConsensus, followBody, it))
            }
        }

        delete("/{id}") {
            val storedConsensus = getConsensusOrThrow("id")

            getAuthUser()?.let {
                if (!ConsensusRepository.isAdmin(storedConsensus, it)) {
                    throw ConsensusException(HttpStatusCode.Forbidden, listOf(Error(ErrorType.FORBIDDEN)))
                }
                call.respond(HttpStatusCode.OK, ConsensusRepository.delete(storedConsensus))
            }
        }
    }
}