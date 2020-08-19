package de.ka.rgreed.dao

import de.ka.rgreed.dao.model.*
import de.ka.rgreed.model.*
import de.ka.rgreed.util.ConsensusTasksManager
import de.ka.rgreed.util.FirebasePushUtil
import de.ka.rgreed.util.HashUtil
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.util.pipeline.PipelineContext
import org.jetbrains.exposed.sql.*

import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

/**
 * Represents the consensus repository class, which does all database operations.
 */
object ConsensusRepository {

    const val TITLE_MIN_LENGTH = 4

    //
    // Utility functions
    //

    /**
     * Finishes a consensus. This is a task most likely to be done somewhere in the future asynchronously.
     *
     * If a consensus is finished, overall acceptance for each suggestion is calculated, if there are any votes on
     * suggestions furthermore, users will get a push notification if they are following that consensus and there
     * has been at least one suggestion.
     *
     * @param consensus the consensus to finish
     */
    fun consensusFinishTask(consensus: ConsensusDao) {
        transaction {
            consensus.isFinished = true
            consensus.endDate = DateTime.now()
            consensus.suggestions.forEach { suggestion ->
                if (!suggestion.votes.empty()) {
                    suggestion.overallAcceptance =
                        suggestion.votes.map(SuggestionsVoteDao::acceptance).average().toFloat()
                }
            }
            if (!consensus.suggestions.empty()) {
                FirebasePushUtil.sendPushTo(
                    getFollowersDistinctPushTokens(consensus),
                    consensus,
                    FirebasePushUtil.PushType.CONSENSUS_FINISHED
                )
            }
            ConsensusTasksManager.unregisterFinishTask(consensus.id.value)
        }
    }

    /**
     * Handle the voting start date reached point of a consensus. This is a task most likely to be done somewhere in
     * the future asynchronously. It should allow users to vote on suggestions, after they could have added some.
     *
     * Followers of that consensus should get a push notification, if there any suggestions.
     *
     * @param consensus the consensus to handle the voting start date of
     */
    fun consensusVotingStarDateReachedTask(consensus: ConsensusDao) {
        transaction {
            ConsensusTasksManager.unregisterVoteTask(consensus.id.value)
            if (consensus.suggestions.empty()) {
                consensusFinishTask(consensus)
                return@transaction
            }
            FirebasePushUtil.sendPushTo(
                getFollowersDistinctPushTokens(consensus),
                consensus,
                FirebasePushUtil.PushType.CONSENSUS_VOTING_START_DATE_REACHED
            )
        }
    }

    /**
     * Registers all unfinished consensus tasks.
     *
     * See [consensusFinishTask] and [consensusVotingStarDateReachedTask] for references of these tasks.
     *
     * Useful for server instance restarts, when it is not sure that all tasks could be handled.
     */
    fun registerAllUnfinishedConsensusTasks() {
        transaction {
            ConsensusDao.find { ConsensusTable.isFinished eq false }.forEach {
                ConsensusTasksManager.registerFinishTask(it)
                ConsensusTasksManager.registerVoteStartTask(it)
            }
        }
    }

    fun isAdmin(consensus: ConsensusDao, user: UsersDao): Boolean {
        return transaction { consensus.admins.any { it.user.id == user.id } }
    }

    fun isAccessor(consensus: ConsensusDao, user: UsersDao): Boolean {
        return transaction { consensus.isPublic || consensus.accessors.any { accessor -> accessor.user.id == user.id } }
    }

    fun isSuggestionTitleAssigned(consensus: ConsensusDao, suggestionBody: SuggestionBody): Boolean {
        return transaction { consensus.suggestions.any { suggestion -> suggestion.title == suggestionBody.title } }
    }

    /**
     * Retrieves the consensus with the given parameter id,
     * or throws a [ConsensusException] with [HttpStatusCode.NotFound] if null.
     *
     * @param param the id param pointing to the consensus
     */
    @Throws(ConsensusException::class)
    fun PipelineContext<Unit, ApplicationCall>.getConsensusOrThrow(param: String): ConsensusDao {
        return call.parameters[param]?.toInt()?.let { transaction { ConsensusDao.findById(it) } }
            ?: throw ConsensusException(HttpStatusCode.NotFound, listOf(Error(ErrorType.RES_NOT_FOUND)))
    }

    /**
     * Analyzes the body of a consensus and throws [ConsensusException] with [HttpStatusCode.BadRequest] if something
     * does not suffice.
     * @return the body, if nothing fails
     */
    @Throws(ConsensusException::class)
    fun ConsensusBody.analyzeConsensusBodyAndThrow(): ConsensusBody {
        val errorList = mutableListOf<Error>()
        if (this.endDate < DateTime.now().millis) {
            errorList.add(Error(ErrorType.CONSENSUS_END, "endDate"))
        }
        if (this.title.length < TITLE_MIN_LENGTH) {
            errorList.add(Error(ErrorType.TITLE_MIN, "titleText"))
        }
        if (this.votingStartDate > this.endDate) {
            errorList.add(Error(ErrorType.VOTING_START, "votingStartDate"))
        }
        if (errorList.isNotEmpty()) {
            throw ConsensusException(HttpStatusCode.BadRequest, errorList)
        }
        return this
    }

    //
    // Building responses
    //

    fun createResponse(item: ConsensusBody, creator: UsersDao): ConsensusResponse {
        return transaction {
            val insertedConsensus = ConsensusDao.new {
                this.title = item.title
                this.description = item.description ?: ""
                this.creationDate = DateTime.now()
                this.creator = creator
                this.isPublic = item.isPublic
                this.endDate = DateTime(item.endDate)
                this.votingStartDate = DateTime(item.votingStartDate)
                this.isFinished = false
                this.privatePassword = if (item.isPublic) "" else HashUtil.hashString(item.privatePassword)
            }
            ConsensusAdminDao.new {
                consensus = insertedConsensus
                user = creator
            }
            ConsensusAccessorDao.new {
                consensus = insertedConsensus
                user = creator
            }
            followIfNotAlreadyFollowing(insertedConsensus, creator)

            ConsensusTasksManager.registerFinishTask(insertedConsensus)
            ConsensusTasksManager.registerVoteStartTask(insertedConsensus)

            insertedConsensus.toResponse(creator)
        }
    }

    fun updateResponse(consensus: ConsensusDao, item: ConsensusBody, user: UsersDao): ConsensusResponse {
        return transaction {
            consensus.apply {
                this.title = item.title
                this.description = item.description ?: ""
                this.isPublic = item.isPublic
                this.endDate = DateTime(item.endDate)
                this.votingStartDate = DateTime(item.votingStartDate)
                this.privatePassword = if (item.isPublic) "" else HashUtil.hashString(item.privatePassword)
            }
            followIfNotAlreadyFollowing(consensus, user)

            ConsensusTasksManager.registerFinishTask(consensus)
            ConsensusTasksManager.registerVoteStartTask(consensus)

            consensus.toResponse(user)
        }
    }

    fun delete(consensus: ConsensusDao): Boolean {
        ConsensusTasksManager.unregisterFinishTask(consensus.id.value)
        ConsensusTasksManager.unregisterVoteTask(consensus.id.value)

        return transaction {
            var deletion = false
            consensus.apply {
                this.suggestions.forEach { SuggestionsRepository.delete(it) }
                this.admins.forEach(ConsensusAdminDao::delete)
                this.accessors.forEach(ConsensusAccessorDao::delete)
                this.followers.forEach(ConsensusFollowersDao::delete)
                this.delete()
                deletion = true
            }
            deletion
        }
    }

    fun getSearchQueryResponse(
        searchQuery: String,
        user: UsersDao? = null,
        limit: Int = 25,
        offset: Int = 0
    ): List<ConsensusResponse> {
        return transaction {
            val select = ConsensusTable.select { ConsensusTable.title like "%$searchQuery%" }
            val query = select.limit(limit, offset).orderBy(ConsensusTable.endDate to SortOrder.DESC)
            ConsensusDao.wrapRows(query).toList().map { it.toResponse(user) }
        }
    }

    fun getAllResponse(
        user: UsersDao? = null,
        limit: Int = 100,
        offset: Int = 0,
        filterFinished: Boolean? = null
    ): List<ConsensusResponse> {
        return transaction {
            val select = if (filterFinished != null) {
                ConsensusTable.select { ConsensusTable.isFinished eq filterFinished }
            } else {
                ConsensusTable.selectAll()
            }
            val query = select.limit(limit, offset).orderBy(ConsensusTable.endDate to SortOrder.DESC)
            ConsensusDao.wrapRows(query).toList().map { it.toResponse(user) }
        }
    }

    fun getConsensusFollowedResponse(
        user: UsersDao,
        limit: Int = 100,
        offset: Int = 0,
        filterFinished: Boolean? = null
    ): List<ConsensusResponse> {
        return transaction {
            val select =
                ConsensusTable.leftJoin(ConsensusFollowersTable)
                    .slice(ConsensusTable.columns)
                    .select { ConsensusFollowersTable.user.eq(user.id) }
            val query = select.limit(limit, offset).orderBy(ConsensusTable.endDate to SortOrder.DESC)

            if (filterFinished != null) {
                query.andWhere { ConsensusTable.isFinished eq filterFinished }
            }

            ConsensusDao.wrapRows(query).toList().map { it.toResponse(user) }
        }
    }

    fun getConsensusAdminResponse(
        user: UsersDao,
        limit: Int = 100,
        offset: Int = 0,
        filterFinished: Boolean? = null
    ): List<ConsensusResponse> {
        return transaction {
            val query =
                ConsensusTable
                    .leftJoin(ConsensusAdminTable)
                    .slice(ConsensusTable.columns)
                    .select { ConsensusAdminTable.user.eq(user.id) }
                    .limit(limit, offset)
                    .orderBy(ConsensusTable.endDate to SortOrder.DESC)
                    .withDistinct()

            if (filterFinished != null) {
                query.andWhere { ConsensusTable.isFinished eq filterFinished }
            }

            ConsensusDao.wrapRows(query).toList().map { it.toResponse(user) }
        }
    }

    fun requestAccessResponse(consensus: ConsensusDao, request: RequestAccessBody, user: UsersDao): ConsensusResponse {
        return transaction {
            if (!consensus.isPublic && HashUtil.hashString(request.password) == consensus.privatePassword) {
                ConsensusAccessorDao.new {
                    this.consensus = consensus
                    this.user = user
                }
            }
            consensus.toResponse(user)
        }
    }

    fun followResponse(consensus: ConsensusDao, followBody: FollowBody, user: UsersDao): ConsensusResponse {
        return transaction {
            val follower = consensus.followers.find { it.user.id == user.id }
            if (follower != null && !followBody.follow) { // if the user is already following, delete it
                follower.delete()
            } else if (follower == null && followBody.follow) { // if does not want to follow: nothing, else create it
                ConsensusFollowersDao.new {
                    this.consensus = consensus
                    this.user = user
                }
            }
            consensus.toResponse(user)
        }
    }

    private fun getDistinctVoters(consensus: ConsensusDao): List<String> {
        val allVoters = mutableListOf<String>()

        consensus.suggestions.forEach { suggestion ->
            allVoters.addAll(suggestion.votes.map { it.voter.userName })
        }
        return allVoters.distinct().toList()
    }

    /**
     * Use this just inside the transaction block!
     */
    private fun getFollowersDistinctPushTokens(consensus: ConsensusDao): List<String> {
        val allTokens = mutableListOf<String>()

        consensus.followers.forEach { follower -> follower.user.pushToken?.let { allTokens.add(it) } }
        return allTokens.distinct().toList()
    }

    /**
     * Use this just inside the transaction block!
     */
    fun followIfNotAlreadyFollowing(consensus: ConsensusDao, user: UsersDao) {
        val follower = consensus.followers.find { it.user.id == user.id }
        if (follower == null) {
            ConsensusFollowersDao.new {
                this.consensus = consensus
                this.user = user
            }
        }
    }

    fun getResponse(consensus: ConsensusDao, user: UsersDao? = null): ConsensusResponse {
        return transaction { consensus.toResponse(user) }
    }

    private fun ConsensusDao.toResponse(user: UsersDao?): ConsensusResponse {
        return ConsensusResponse(
            id = this.id.value,
            title = this.title,
            description = this.description,
            creationDate = this.creationDate.millis,
            creator = this.creator.userName,
            suggestionsCount = this.suggestions.count(),
            admin = this.admins.any { it.user.id == user?.id },
            public = this.isPublic,
            endDate = this.endDate.millis,
            voters = getDistinctVoters(this),
            finished = this.isFinished,
            hasAccess = if (isPublic) true else this.accessors.any { it.user.id == user?.id },
            votingStartDate = this.votingStartDate.millis,
            following = this.followers.any { it.user.id == user?.id }
        )
    }
}