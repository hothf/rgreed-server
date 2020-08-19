package de.ka.rgreed.dao

import de.ka.rgreed.dao.ConsensusRepository.followIfNotAlreadyFollowing
import de.ka.rgreed.dao.model.*
import de.ka.rgreed.model.*
import io.ktor.util.pipeline.PipelineContext
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode

import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

/**
 * Represents the consensus repository class, which does all database operations.
 */
object SuggestionsRepository {

    //
    // Utility functions
    //

    /**
     * Checks whether the given suggestion id, consensus id and user id depict, that the caller can edit
     * suggestions.
     *
     * **Requires initialization of all databases first.**
     *
     * @param suggestion the suggestion to check
     * @param consensus the consensus to check
     * @param user the user
     * @return true if admin, false otherwise
     */
    fun isAdmin(suggestion: SuggestionsDao, consensus: ConsensusDao, user: UsersDao): Boolean {
        val isCreator = transaction { suggestion.creator.id == user.id }

        return isCreator || ConsensusRepository.isAdmin(consensus, user)
    }

    /**
     * Retrieves the suggestion with the given parameter id,
     * or throws a [ConsensusException] with [HttpStatusCode.NotFound] if null or not a suggestion of the given
     * consensus.
     *
     * @param param the param id pointing to the suggestion
     * @param consensus the consensus
     */
    @Throws(ConsensusException::class)
    fun PipelineContext<Unit, ApplicationCall>.getSuggestionOrThrow(
        param: String,
        consensus: ConsensusDao
    ): SuggestionsDao {
        return call.parameters[param]?.toInt()?.let {
            transaction {
                val suggestion = SuggestionsDao.findById(it)
                if (suggestion?.consensus?.id == consensus.id) {
                    suggestion
                } else null
            }
        } ?: throw ConsensusException(HttpStatusCode.NotFound, listOf(Error(ErrorType.RES_NOT_FOUND)))
    }

    /**
     * Analyzes the body of a suggestion and throws [ConsensusException] with [HttpStatusCode.BadRequest] if something
     * does not suffice.
     * @return the body, if nothing fails
     */
    @Throws(ConsensusException::class)
    fun SuggestionBody.analyzeSuggestionBodyAndThrow(): SuggestionBody {
        val errorList = mutableListOf<Error>()
        if (this.title.length < ConsensusRepository.TITLE_MIN_LENGTH) {
            errorList.add(Error(ErrorType.TITLE_MIN, "titleText"))
        }
        if (errorList.isNotEmpty()) {
            throw ConsensusException(HttpStatusCode.BadRequest, errorList)
        }
        return this
    }

    //
    // Building responses
    //

    fun createResponse(consensus: ConsensusDao, item: SuggestionBody, user: UsersDao): SuggestionResponse {
        return transaction {
            val insertedSuggestion = SuggestionsDao.new {
                this.consensus = consensus
                this.creationDate = DateTime.now()
                this.creator = user
                this.overallAcceptance = null
                this.title = item.title
            }
            followIfNotAlreadyFollowing(consensus, user)
            insertedSuggestion.toResponse(user)
        }
    }

    fun updateResponse(suggestion: SuggestionsDao, item: SuggestionBody, user: UsersDao): SuggestionResponse {
        return transaction {
            suggestion.apply {
                this.title = item.title
            }
            followIfNotAlreadyFollowing(suggestion.consensus, user)
            suggestion.toResponse(user)
        }
    }

    fun voteResponse(suggestion: SuggestionsDao, item: VoteBody, user: UsersDao): SuggestionResponse {
        return transaction {
            val updatedSuggestion = suggestion.apply {
                val vote = votes.find { it.voter.id == user.id }
                if (vote != null) { // update
                    vote.acceptance = item.acceptance
                } else { // create
                    SuggestionsVoteDao.new {
                        this.acceptance = item.acceptance
                        this.suggestion = this@apply
                        this.voter = user
                    }
                }
                followIfNotAlreadyFollowing(consensus, user)
            }
            updatedSuggestion.toResponse(user)
        }

    }

    fun delete(suggestion: SuggestionsDao): Boolean {
        return transaction {
            var deletion = false
            suggestion.apply {
                this.votes.forEach(SuggestionsVoteDao::delete)
                this.delete()
                deletion = true
            }
            deletion
        }
    }

    /**
     * Retrieves all suggestions of a consensus. If a user id is provided, checks if the suggestion has relations to a user with that id.
     */
    fun getAllAndRespond(consensus: ConsensusDao, user: UsersDao? = null): List<SuggestionResponse> {
        return transaction {
            consensus.suggestions.map { it.toResponse(user) }
        }
    }

    fun getResponse(suggestion: SuggestionsDao, user: UsersDao? = null): SuggestionResponse {
        return transaction { suggestion.toResponse(user) }
    }

    private fun userAcceptance(suggestion: SuggestionsDao, user: UsersDao?): Float? {
        if (user == null) {
            return null
        }

        return transaction {
            suggestion.votes.find { it.voter.id == user.id }?.acceptance
        }
    }

    private fun SuggestionsDao.toResponse(user: UsersDao?): SuggestionResponse {
        return SuggestionResponse(
            id = this.id.value,
            title = this.title,
            creationDate = this.creationDate.millis,
            consensusId = this.consensus.id.value,
            overallAcceptance = this.overallAcceptance,
            admin = if (user == null) false else isAdmin(this, this.consensus, user),
            ownAcceptance = userAcceptance(this, user),
            voters = this.votes.map { it.voter.userName },
            heavyObjectionsCount = if (this.consensus.isFinished) this.votes.count { it.acceptance == 10.0f } else null
        )
    }
}