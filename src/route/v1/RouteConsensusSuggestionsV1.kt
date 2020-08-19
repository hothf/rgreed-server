package de.ka.rgreed.route.v1

import de.ka.rgreed.dao.ConsensusRepository
import de.ka.rgreed.dao.ConsensusRepository.getConsensusOrThrow
import de.ka.rgreed.dao.SuggestionsRepository
import de.ka.rgreed.dao.SuggestionsRepository.getSuggestionOrThrow
import de.ka.rgreed.dao.SuggestionsRepository.analyzeSuggestionBodyAndThrow
import de.ka.rgreed.model.*
import de.ka.rgreed.util.getAuthUser
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*


/**
 * This file represents a kotlin extension function for routesV1 v1 calls.
 *
 * It shows all the available controller for consuming the api with the different actions.
 */
fun Route.routeConsensusSuggestionsV1() {

    authenticate(optional = true) {
        get("/") {
            val storedConsensus = getConsensusOrThrow("consensusId")

            getAuthUser().let {
                call.respond(SuggestionsRepository.getAllAndRespond(storedConsensus, it))
            }
        }

        get("/{id}") {
            val storedConsensus = getConsensusOrThrow("consensusId")
            val storedSuggestion = getSuggestionOrThrow("id", storedConsensus)

            getAuthUser().let {
                call.respond(SuggestionsRepository.getResponse(storedSuggestion, it))
            }
        }
    }

    authenticate {
        post("/") {
            val storedConsensus = getConsensusOrThrow("consensusId")
            val suggestionBody = call.receive<SuggestionBody>().analyzeSuggestionBodyAndThrow()

            if (storedConsensus.isFinished || System.currentTimeMillis() > storedConsensus.votingStartDate.millis) {
                throw ConsensusException(HttpStatusCode.BadRequest, listOf(Error(ErrorType.BAD_REQUEST)))
            }
            getAuthUser()?.let { user ->
                if (!ConsensusRepository.isAccessor(storedConsensus, user)) {
                    throw ConsensusException(HttpStatusCode.Forbidden, listOf(Error(ErrorType.FORBIDDEN)))
                }

                if (ConsensusRepository.isSuggestionTitleAssigned(storedConsensus, suggestionBody)) {
                    throw ConsensusException(
                        HttpStatusCode.BadRequest,
                        listOf(Error(ErrorType.DUPLICATED_SUGGESTION_TITLE, "titleText"))
                    )
                }

                call.respond(
                    HttpStatusCode.Created,
                    SuggestionsRepository.createResponse(storedConsensus, suggestionBody, user)
                )
            }
        }

        put("/{id}") {
            val storedConsensus = getConsensusOrThrow("consensusId")
            val storedSuggestion = getSuggestionOrThrow("id", storedConsensus)
            val suggestionBody = call.receive<SuggestionBody>().analyzeSuggestionBodyAndThrow()

            if (storedConsensus.isFinished || System.currentTimeMillis() > storedConsensus.votingStartDate.millis) {
                throw ConsensusException(HttpStatusCode.BadRequest, listOf(Error(ErrorType.BAD_REQUEST)))
            }

            getAuthUser()?.let {
                if (!SuggestionsRepository.isAdmin(storedSuggestion, storedConsensus, it)
                    || !ConsensusRepository.isAccessor(storedConsensus, it)
                ) {
                    throw ConsensusException(HttpStatusCode.Forbidden, listOf(Error(ErrorType.FORBIDDEN)))
                }
                call.respond(SuggestionsRepository.updateResponse(storedSuggestion, suggestionBody, it))
            }
        }

        post("/{id}/vote") {
            val storedConsensus = getConsensusOrThrow("consensusId")
            val storedSuggestion = getSuggestionOrThrow("id", storedConsensus)
            val voteBody = call.receive<VoteBody>()

            if (System.currentTimeMillis() < storedConsensus.votingStartDate.millis || storedConsensus.isFinished) {
                throw ConsensusException(HttpStatusCode.BadRequest, listOf(Error(ErrorType.BAD_REQUEST)))
            }

            if (voteBody.acceptance > 10 || voteBody.acceptance < 0) {
                throw ConsensusException(HttpStatusCode.BadRequest, listOf(Error(ErrorType.BAD_VOTING_RANGE, "vote")))
            }

            getAuthUser()?.let {
                if (!ConsensusRepository.isAccessor(storedConsensus, it)) {
                    throw ConsensusException(HttpStatusCode.Forbidden, listOf(Error(ErrorType.FORBIDDEN)))
                }
                call.respond(SuggestionsRepository.voteResponse(storedSuggestion, voteBody, it))
            }
        }

        delete("/{id}") {
            val storedConsensus = getConsensusOrThrow("consensusId")
            val storedSuggestion = getSuggestionOrThrow("id", storedConsensus)

            if (System.currentTimeMillis() > storedConsensus.votingStartDate.millis) {
                throw ConsensusException(HttpStatusCode.BadRequest, listOf(Error(ErrorType.BAD_REQUEST)))
            }

            getAuthUser()?.let {
                if (!SuggestionsRepository.isAdmin(storedSuggestion, storedConsensus, it)
                    || !ConsensusRepository.isAccessor(storedConsensus, it)
                ) {
                    throw ConsensusException(HttpStatusCode.Forbidden, listOf(Error(ErrorType.FORBIDDEN)))
                }
                call.respond(HttpStatusCode.OK, SuggestionsRepository.delete(storedSuggestion))
            }
        }
    }
}