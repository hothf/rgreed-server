import de.ka.rgreed.dao.ConsensusRepository
import de.ka.rgreed.dao.SuggestionsRepository
import de.ka.rgreed.dao.UsersRepository
import de.ka.rgreed.dao.model.*
import de.ka.rgreed.model.*
import de.ka.rgreed.module
import de.ka.rgreed.util.ConsensusTasksManager
import io.ktor.application.Application
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Test
import util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Intended for testing consensus calls and responses.
 */
class ConsensusTest {

    private lateinit var authHeader1: String
    private lateinit var authHeader2: String

    @Test
    fun `should show all consensus and specific ones`() = withTestApplication(Application::module) {

        DatabaseUtil.initialize()

        authHeader1 = createAuthenticatedUserToken(userName = "User1")
        authHeader2 = createAuthenticatedUserToken(userName = "User2")


        // create sample consensus data
        ConsensusRepository.createResponse(
            ConsensusBody(title = "Hello1", endDate = FAR_FUTURE, votingStartDate = FAR_FUTURE),
            UsersRepository.findUserByName("User1")!!
        )
        ConsensusRepository.createResponse(
            ConsensusBody(title = "Hello2", endDate = FAR_FUTURE + 1, votingStartDate = FAR_FUTURE),
            UsersRepository.findUserByName("User2")!!
        )
        ConsensusRepository.createResponse(
            ConsensusBody(title = "Hello3", endDate = FAR_FUTURE + 2, votingStartDate = FAR_FUTURE),
            UsersRepository.findUserByName("User2")!!
        )
        ConsensusRepository.createResponse(
            ConsensusBody(title = "Hello4", endDate = FAR_FUTURE + 3, votingStartDate = FAR_FUTURE),
            UsersRepository.findUserByName("User2")!!
        )

        assertEquals(4, ConsensusRepository.getAllResponse().size)

        //
        // GET / (All)
        //

        // successful getting
        val consensusGetResponse = getResponse<ArrayList<ConsensusResponse>>("v1/consensus", print = true)

        assertEquals(HttpStatusCode.OK, consensusGetResponse.response?.status())
        assertEquals(4, consensusGetResponse.mappedData?.size)
        assertFalse(consensusGetResponse.mappedData?.get(0)?.admin!!)
        assertEquals(0, consensusGetResponse.mappedData?.get(0)?.voters?.size)

        // successful getting, authorized
        val consensusGetResponseAuthenticated = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, consensusGetResponseAuthenticated.response?.status())
        assertEquals(4, consensusGetResponseAuthenticated.mappedData?.size)
        assertFalse(consensusGetResponseAuthenticated.mappedData?.get(0)?.admin!!)
        assertTrue(consensusGetResponseAuthenticated.mappedData?.get(3)?.admin!!)

        // successful getting for second, authorized
        val consensusGetResponseSecondAuthenticated = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus",
            headers = mapOf("Authorization" to "Bearer $authHeader2")
        )

        val consensusId = consensusGetResponseSecondAuthenticated.mappedData?.get(3)?.id

        assertEquals(HttpStatusCode.OK, consensusGetResponseSecondAuthenticated.response?.status())
        assertEquals(4, consensusGetResponseSecondAuthenticated.mappedData?.size)
        assertFalse(consensusGetResponseSecondAuthenticated.mappedData?.get(3)?.admin!!)
        assertTrue(consensusGetResponseSecondAuthenticated.mappedData?.get(0)?.admin!!)

        //
        // GET {id}
        //

        val unsuccesfulGetAuthorized = getResponse<ConsensusResponse>(
            "v1/consensus/-1",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.NotFound, unsuccesfulGetAuthorized.response?.status())

        val succesfulGetAuthorized1 = getResponse<ConsensusResponse>(
            "v1/consensus/$consensusId",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, succesfulGetAuthorized1.response?.status())
        assertEquals("Hello1", succesfulGetAuthorized1.mappedData?.title)
        assertTrue(succesfulGetAuthorized1.mappedData?.admin!!)

        val succesfulGetAuthorized2 = getResponse<ConsensusResponse>(
            "v1/consensus/$consensusId",
            headers = mapOf("Authorization" to "Bearer $authHeader2")
        )

        assertEquals(HttpStatusCode.OK, succesfulGetAuthorized2.response?.status())
        assertEquals("Hello1", succesfulGetAuthorized2.mappedData?.title)
        assertFalse(succesfulGetAuthorized2.mappedData?.admin!!)
    }

    @Test
    fun `should post, update and delete a consensus`() = withTestApplication(Application::module) {

        DatabaseUtil.initialize()

        authHeader1 = createAuthenticatedUserToken(userName = "User1")
        authHeader2 = createAuthenticatedUserToken(userName = "User2")

        //
        // POST
        //

        val unsuccessfulPost = postResponse<ConsensusResponse>(
            route = "v1/consensus",
            postBody = ConsensusBody(
                "new ConsensusDao",
                endDate = FAR_FUTURE,
                votingStartDate = System.currentTimeMillis()
            )
        )

        assertEquals(HttpStatusCode.Unauthorized, unsuccessfulPost.response?.status())

        val unsuccessfulDatePost = postResponse<ConsensusResponse>(
            route = "v1/consensus",
            postBody = ConsensusBody(
                "new ConsensusDao",
                endDate = DateTime.now().millis,
                votingStartDate = System.currentTimeMillis()
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, unsuccessfulDatePost.response?.status())

        val shortTitlePost = postResponse<ConsensusResponse>(
            route = "v1/consensus",
            postBody = ConsensusBody(
                "123",
                endDate = DateTime.now().millis,
                votingStartDate = System.currentTimeMillis()
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, shortTitlePost.response?.status())

        val successfulPost = postResponse<ConsensusResponse>(
            route = "v1/consensus",
            postBody = ConsensusBody(
                "new ConsensusDao",
                endDate = FAR_FUTURE,
                votingStartDate = System.currentTimeMillis()
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.Created, successfulPost.response?.status())
        assertEquals("new ConsensusDao", successfulPost.mappedData?.title)

        val consensusId = successfulPost.mappedData?.id!!

        assertTrue(ConsensusTasksManager.finishTaskMap.keys.any { consensusId == it })
        assertEquals(1, ConsensusTasksManager.finishTaskMap.size)


        //
        // PUT {id}
        //

        val unsuccessfulPut = putResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId",
            putBody = ConsensusBody(
                "updated ConsensusDao",
                endDate = FAR_FUTURE,
                votingStartDate = System.currentTimeMillis()
            )
        )

        assertEquals(HttpStatusCode.Unauthorized, unsuccessfulPut.response?.status())

        val unsuccessfulDatePut = putResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId",
            putBody = ConsensusBody(
                "updated ConsensusDao",
                endDate = DateTime.now().millis,
                votingStartDate = System.currentTimeMillis()
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, unsuccessfulDatePut.response?.status())

        val successfulPut = putResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId",
            putBody = ConsensusBody(
                "updated ConsensusDao",
                endDate = FAR_FUTURE,
                votingStartDate = System.currentTimeMillis()
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, successfulPut.response?.status())
        assertEquals("updated ConsensusDao", successfulPut.mappedData?.title)

        assertEquals(1, ConsensusTasksManager.finishTaskMap.size)
        assertEquals(consensusId, ConsensusTasksManager.finishTaskMap.keys.first())


        val slightlyFuture = DateTime.now().millis + 1_000

        val successfulPutWithEnd = putResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId",
            putBody = ConsensusBody(
                "updated ConsensusDao",
                endDate = slightlyFuture,
                votingStartDate = System.currentTimeMillis()
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, successfulPutWithEnd.response?.status())
        assertEquals(slightlyFuture, successfulPutWithEnd.mappedData?.endDate)
        assertFalse(successfulPutWithEnd.mappedData?.finished!!)

        runBlocking {
            delay(2_000)
        }

        transaction {
            ConsensusDao.findById(consensusId)!!.let {
                val consensus = ConsensusRepository.getResponse(it)
                assertTrue(consensus.finished)
                assertEquals(0, ConsensusTasksManager.finishTaskMap.size)
            }
        }

        val pastEndingDatePut = putResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId",
            putBody = ConsensusBody(
                "updated ConsensusDao",
                endDate = DateTime.now().millis,
                votingStartDate = System.currentTimeMillis()
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, pastEndingDatePut.response?.status())

        val alreadyFinishedPut = putResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId",
            putBody = ConsensusBody(
                "updated ConsensusDao",
                endDate = FAR_FUTURE,
                votingStartDate = System.currentTimeMillis()
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, alreadyFinishedPut.response?.status())

        //
        // DELETE
        //

        val unsuccessfulDelete = deleteResponse<Any>(route = "v1/consensus/$consensusId")

        assertEquals(HttpStatusCode.Unauthorized, unsuccessfulDelete.response?.status())

        val successfulDelete = deleteResponse<Any>(
            route = "v1/consensus/$consensusId",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, successfulDelete.response?.status())

        assertEquals(0, ConsensusTasksManager.finishTaskMap.size)


        assertTrue(ConsensusRepository.getAllResponse().isEmpty())

    }

    @Test
    fun `should manipulate consensus with suggestions`() = withTestApplication(Application::module) {

        DatabaseUtil.initialize()

        authHeader1 = createAuthenticatedUserToken(userName = "User1")
        authHeader2 = createAuthenticatedUserToken(userName = "User2")


        // create sample consensus data
        ConsensusRepository.createResponse(
            ConsensusBody(
                title = "Hello1",
                endDate = System.currentTimeMillis() + 2500,
                votingStartDate = System.currentTimeMillis() + 1_500
            ),
            UsersRepository.findUserByName("User1")!!
        )
        ConsensusRepository.createResponse(
            ConsensusBody(title = "Hello2", endDate = FAR_FUTURE, votingStartDate = System.currentTimeMillis() + 1_500),
            UsersRepository.findUserByName("User1")!!
        )
        ConsensusRepository.createResponse(
            ConsensusBody(title = "Hello3", endDate = FAR_FUTURE, votingStartDate = System.currentTimeMillis() + 1_500),
            UsersRepository.findUserByName("User1")!!
        )
        ConsensusRepository.createResponse(
            ConsensusBody(title = "Hello4", endDate = FAR_FUTURE, votingStartDate = System.currentTimeMillis() + 1_500),
            UsersRepository.findUserByName("User1")!!
        )

        val consensusId1 = ConsensusRepository.getAllResponse()[0].id

        assertEquals(4, ConsensusRepository.getAllResponse().size)

        //
        // POST
        //

        val unsuccessfulPost = postResponse<SuggestionResponse>(
            route = "v1/consensus/-1/suggestions",
            postBody = SuggestionBody("Suggestuin title"),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.NotFound, unsuccessfulPost.response?.status())

        val emptyTitlePost = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions",
            postBody = SuggestionBody(""),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, emptyTitlePost.response?.status())

        val blankTitlePost = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions",
            postBody = SuggestionBody("  "),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, blankTitlePost.response?.status())

        val shortTitlePost = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions",
            postBody = SuggestionBody("123"),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, shortTitlePost.response?.status())

        val duplicatedTitlePost = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions",
            postBody = SuggestionBody("123"),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, duplicatedTitlePost.response?.status())

        val successfulPost = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/",
            postBody = SuggestionBody("Suggestion title"),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.Created, successfulPost.response?.status())
        assertTrue(successfulPost.mappedData?.admin!!)

        val suggestionsId = successfulPost.mappedData?.id!!

        val successfulPost2 = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/",
            postBody = SuggestionBody("Suggestion title 2"),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )
        val suggestionsId2 = successfulPost2.mappedData?.id!!

        transaction {
            SuggestionsDao.findById(suggestionsId)!!.let {
                assertTrue(SuggestionsRepository.getResponse(it, UsersRepository.findUserByName("User1")).admin)
                assertFalse(SuggestionsRepository.getResponse(it, UsersRepository.findUserByName("User2")).admin)
            }
        }

        val getResponse = getResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId1",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )
        assertEquals(2, getResponse.mappedData?.suggestionsCount)
        assertTrue(getResponse.mappedData!!.following)

        //
        // PUT {id}
        //

        val putResponse1 = putResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/$suggestionsId",
            putBody = SuggestionBody("put title"),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, putResponse1.response?.status())

        transaction {
            assertEquals("put title", SuggestionsRepository.getResponse(SuggestionsDao.findById(suggestionsId)!!).title)
        }

        val putResponse2 = putResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/$suggestionsId",
            putBody = SuggestionBody("put title"),
            headers = mapOf("Authorization" to "Bearer $authHeader2")
        )

        assertEquals(HttpStatusCode.Forbidden, putResponse2.response?.status())

        //
        // GET SUGGESTIONS
        //

        val responseGet1 = getResponse<List<SuggestionResponse>>(route = "v1/consensus/$consensusId1/suggestions")

        assertEquals(HttpStatusCode.OK, responseGet1.response?.status())
        assertEquals(2, responseGet1.mappedData?.size)

        val responseGet2 = getResponse<List<SuggestionResponse>>(
            route = "v1/consensus/$consensusId1/suggestions",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertTrue(responseGet2.mappedData?.get(0)?.admin!!)
        assertEquals(2, responseGet2.mappedData?.size)

        val responseGet3 = getResponse<List<SuggestionResponse>>(
            route = "v1/consensus/$consensusId1/suggestions",
            headers = mapOf("Authorization" to "Bearer $authHeader2")
        )

        assertFalse(responseGet3.mappedData?.get(0)?.admin!!)

        //
        // GET SUGGESTION {id}
        //

        val responseGetId1 =
            getResponse<SuggestionResponse>(route = "v1/consensus/$consensusId1/suggestions/$suggestionsId")

        assertEquals(HttpStatusCode.OK, responseGetId1.response?.status())
        assertFalse(responseGetId1.mappedData?.admin!!)

        val responseGetId2 = getResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/$suggestionsId",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, responseGetId2.response?.status())
        assertTrue(responseGetId2.mappedData?.admin!!)

        //
        // GET CONSENSUS SUGGESTION
        //

        val consensusSuggestionResponse = getResponse<List<SuggestionResponse>>(
            route = "v1/consensus/$consensusId1/suggestions/",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, consensusSuggestionResponse.response?.status())
        assertEquals(2, consensusSuggestionResponse.mappedData?.size)

        //
        // DELETE SUGGESTION {id}
        //

        val deleteResponse1 = deleteResponse<Any>(
            route = "v1/consensus/$consensusId1/suggestions/$suggestionsId",
            headers = mapOf("Authorization" to "Bearer $authHeader2")
        )

        assertEquals(HttpStatusCode.Forbidden, deleteResponse1.response?.status())

        val deleteResponse2 = deleteResponse<Any>(
            route = "v1/consensus/$consensusId1/suggestions/-1",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.NotFound, deleteResponse2.response?.status())

        val deleteResponse3 = deleteResponse<Any>(
            route = "v1/consensus/$consensusId1/suggestions/$suggestionsId",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, deleteResponse3.response?.status())

        val testGet = getResponse<List<SuggestionResponse>>(route = "v1/consensus/$consensusId1/suggestions")

        assertEquals(1, testGet.mappedData?.size)

        //
        // POST {id} FOLLOW
        //

        val followPost1 = postResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId1/follow",
            postBody = FollowBody(false),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, followPost1.response?.status())
        assertFalse(followPost1.mappedData!!.following)

        val followPost2 = postResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId1/follow",
            postBody = FollowBody(true),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, followPost2.response?.status())
        assertTrue(followPost2.mappedData!!.following)

        val followPost3 = postResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId1/follow",
            postBody = FollowBody(false),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, followPost3.response?.status())
        assertFalse(followPost3.mappedData!!.following)

        //
        // POST {id} VOTE
        //

        // wait 1500 ms because the voting start time has to be passed
        runBlocking {
            delay(1_500)
        }

        val votePost1 = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/$suggestionsId2/vote",
            postBody = VoteBody(5.0f),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, votePost1.response?.status())
        assertTrue(votePost1.mappedData?.admin!!)
        assertEquals(5.0f, votePost1.mappedData?.ownAcceptance)
        assertEquals("User1", votePost1.mappedData?.voters?.get(0))
        assertEquals(1, votePost1.mappedData?.voters?.count())

        transaction {
            assertNull(SuggestionsDao.findById(suggestionsId2)!!.overallAcceptance)
        }

        val votePost2 = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/$suggestionsId2/vote",
            postBody = VoteBody(10.0f),
            headers = mapOf("Authorization" to "Bearer $authHeader2")
        )

        assertEquals(HttpStatusCode.OK, votePost2.response?.status())
        assertFalse(votePost2.mappedData?.admin!!)
        assertEquals(10.0f, votePost2.mappedData?.ownAcceptance)

        // check if participant count is correct:
        transaction {
            assertEquals(
                2,
                ConsensusRepository.getResponse(ConsensusDao.findById(consensusId1)!!, null).voters.size
            )
            assertEquals(
                "User2",
                ConsensusRepository.getResponse(ConsensusDao.findById(consensusId1)!!, null).voters[1]
            )
        }
        assertEquals("User2", votePost2.mappedData?.voters?.get(1))
        assertEquals(2, votePost2.mappedData?.voters?.count())

        val votePost3 = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/$suggestionsId2/vote",
            postBody = VoteBody(9.0f),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, votePost3.response?.status())
        assertEquals(9.0f, votePost3.mappedData?.ownAcceptance)

        // check if participant count still is correct, this should not have changed:
        transaction {
            assertEquals(
                2,
                ConsensusRepository.getResponse(ConsensusDao.findById(consensusId1)!!, null).voters.size
            )
            assertEquals(
                "User2",
                ConsensusRepository.getResponse(ConsensusDao.findById(consensusId1)!!, null).voters[1]
            )
        }
        assertEquals("User2", votePost3.mappedData?.voters?.get(1))
        assertEquals(2, votePost3.mappedData?.voters?.count())


        val votePost4 = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/-1/vote",
            postBody = VoteBody(25.0f),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.NotFound, votePost4.response?.status())

        val votePost5 = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId1/suggestions/$suggestionsId2/vote",
            postBody = VoteBody(25.0f),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.BadRequest, votePost5.response?.status())


        transaction {
            ConsensusRepository.updateResponse(
                ConsensusDao.findById(consensusId1)!!,
                ConsensusBody(
                    "title",
                    endDate = System.currentTimeMillis() + 10,
                    votingStartDate = System.currentTimeMillis()
                ), UsersRepository.findUserByName("User1")!!
            )
        }

        runBlocking {
            delay(100)
        }

        transaction {
            assertEquals(9.5f, SuggestionsDao.findById(suggestionsId2)!!.overallAcceptance)
        }
    }

    @Test
    fun `full db integrity consensus post, update, vote, delete`() = withTestApplication(Application::module) {

        DatabaseUtil.initialize()

        // 1. Wipe all databases and initialize a user

        authHeader1 = createAuthenticatedUserToken(userName = "User1")

        // 2. post a consensus
        val postConsensus = postResponse<ConsensusResponse>(
            "v1/consensus",
            postBody = ConsensusBody(
                "New Test ConsensusDao",
                endDate = FAR_FUTURE,
                votingStartDate = System.currentTimeMillis() + 1_500
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        val consensusId = postConsensus.mappedData?.id!!
        val user = UsersRepository.findUserByName("User1")

        // 3. check if stored correctly, update it
        transaction {
            ConsensusDao.findById(consensusId)!!.let {
                assertEquals("New Test ConsensusDao", ConsensusRepository.getResponse(it, user).title)
                assertTrue(ConsensusRepository.getResponse(it, user).admin)
            }
        }

        putResponse<ConsensusResponse>(
            "v1/consensus/$consensusId",
            putBody = ConsensusBody(
                "Updated Test ConsensusDao",
                endDate = FAR_FUTURE,
                votingStartDate = System.currentTimeMillis() + 1_500
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        transaction {
            ConsensusDao.findById(consensusId)!!.let {
                assertEquals("Updated Test ConsensusDao", ConsensusRepository.getResponse(it, user).title)
                assertTrue(ConsensusRepository.getResponse(it, user).admin)
            }
        }

        // 4. add a suggestion to the consensus and check if stored correctly
        val postSuggestion = postResponse<SuggestionResponse>(
            route = "v1/consensus/$consensusId/suggestions",
            postBody = SuggestionBody(
                title = "New Test Suggestion"
            ),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        val suggestionId = postSuggestion.mappedData?.id!!

        transaction {
            SuggestionsDao.findById(suggestionId)!!.let {
                assertEquals("New Test Suggestion", SuggestionsRepository.getResponse(it, user).title)
                assertTrue(SuggestionsRepository.getResponse(it, user).admin)
            }
            assertEquals(
                1,
                ConsensusRepository.getResponse(ConsensusDao.findById(consensusId)!!, user).suggestionsCount
            )
        }

        // 5. follow the consensus
        postResponse<ConsensusResponse>(
            route = "v1/consensus/$consensusId/follow",
            postBody = FollowBody(true),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        // 6. vote on the suggestion of the consensus
        postResponse<Any>(
            route = "v1/consensus/$consensusId/suggestions/$suggestionId/vote",
            postBody = VoteBody(12.0f),
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        // 7. delete the consensus
        deleteResponse<Any>(
            route = "v1/consensus/$consensusId",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        // 8. check the consensus & suggestion & Vote db & admins db
        assertTrue(ConsensusRepository.getAllResponse().isEmpty())

        transaction {
            assertEquals(0, SuggestionsVoteTable.selectAll().count())
            assertEquals(0, ConsensusAdminTable.selectAll().count())
            assertEquals(0, ConsensusFollowersTable.selectAll().count())
        }
    }

    @Test
    fun `should run all unregistered consensus tasks`() = withTestApplication(Application::module) {

        DatabaseUtil.initialize()

        authHeader1 = createAuthenticatedUserToken(userName = "User1")

        ConsensusRepository.createResponse(
            ConsensusBody(title = "unregistered1", endDate = FAR_FUTURE, votingStartDate = System.currentTimeMillis()),
            UsersRepository.findUserByName("User1")!!
        )
        ConsensusRepository.createResponse(
            ConsensusBody(title = "unregistered2", endDate = FAR_FUTURE, votingStartDate = System.currentTimeMillis()),
            UsersRepository.findUserByName("User1")!!
        )

        val size = ConsensusTasksManager.finishTaskMap.size

        ConsensusRepository.registerAllUnfinishedConsensusTasks()

        assertEquals(size, ConsensusTasksManager.finishTaskMap.size)


    }

    @Test
    fun `should finish consensus without suggestions`() = withTestApplication(Application::module) {

        DatabaseUtil.initialize()

        authHeader1 = createAuthenticatedUserToken(userName = "User1")

        ConsensusRepository.createResponse(
            ConsensusBody(title = "unregistered1", endDate = System.currentTimeMillis() + 1500, votingStartDate = System.currentTimeMillis()),
            UsersRepository.findUserByName("User1")!!
        )
        ConsensusRepository.createResponse(
            ConsensusBody(title = "unregistered2", endDate = System.currentTimeMillis() + 1500, votingStartDate = System.currentTimeMillis()),
            UsersRepository.findUserByName("User1")!!
        )

        // 2 open consensuses
        val openConsensusResponse = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus/following?finished=true",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        val openList = mutableListOf<ConsensusResponse>()
        openConsensusResponse.mappedData?.forEach { response -> if (response.finished) openList.add(response) }
        assertEquals(0, openList.size)

        runBlocking {
            delay(2_000)
        }

        // no more open consensuses, because no suggestions were added
        val consensusResponse = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus/following?finished=true",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        val finishedList = mutableListOf<ConsensusResponse>()
        consensusResponse.mappedData?.forEach { response -> if (response.finished) finishedList.add(response) }
        assertEquals(2, finishedList.size)
    }

    @Test
    fun `should test pagination`() = withTestApplication(Application::module) {
        DatabaseUtil.initialize()

        authHeader1 = createAuthenticatedUserToken(userName = "User1")

        transaction {
            (0..100).forEach {
                val body = ConsensusBody(
                    "$it",
                    endDate = System.currentTimeMillis() + 1000000 + it,
                    votingStartDate = System.currentTimeMillis()
                )
                ConsensusRepository.createResponse(body, UsersRepository.findUserByName("User1")!!)
            }
        }

        val getResponse1 = getResponse<ArrayList<ConsensusResponse>>(
            route = "v1/consensus?limit=5&offset=0"
        )

        assertEquals(5, getResponse1.mappedData?.size)
        assertEquals("100", getResponse1.mappedData?.get(0)?.title)

        val getResponse2 = getResponse<ArrayList<ConsensusResponse>>(
            route = "v1/consensus?limit=5&offset=5"
        )

        assertEquals(5, getResponse2.mappedData?.size)
        assertEquals("95", getResponse2.mappedData?.get(0)?.title)
    }

    @Test
    fun `should test admin and follower`() = withTestApplication(Application::module) {

        // admin are all consensuses where either the user asking for its admin consensuses:
        // - is admin of a consensus (this should always include the creator flag)

        DatabaseUtil.initialize()

        authHeader1 = createAuthenticatedUserToken(userName = "User1")
        authHeader2 = createAuthenticatedUserToken(userName = "User2")
        val authHeader3 = createAuthenticatedUserToken(userName = "User3")

        transaction {
            (1..5).forEach {
                val body = ConsensusBody(
                    "$it",
                    endDate = System.currentTimeMillis() + 2000,
                    votingStartDate = System.currentTimeMillis()
                )
                ConsensusRepository.createResponse(body, UsersRepository.findUserByName("User1")!!)
            }
            (1..5).forEach {
                val body = ConsensusBody(
                    "$it",
                    endDate = System.currentTimeMillis() + 2000,
                    votingStartDate = System.currentTimeMillis()
                )
                ConsensusRepository.createResponse(body, UsersRepository.findUserByName("User2")!!)
            }
            (1..5).forEach {
                val body = ConsensusBody(
                    "$it",
                    endDate = System.currentTimeMillis() + 2000,
                    votingStartDate = System.currentTimeMillis()
                )
                ConsensusRepository.createResponse(body, UsersRepository.findUserByName("User3")!!)
            }
        }

        // we see that user2 has exactly its created consensuses as admin

        var list = ConsensusRepository.getConsensusAdminResponse(UsersRepository.findUserByName("User2")!!)
        println(list.toString())
        assertEquals(5, list.size)

        // we add the user2 as admin to two consensuses created from someone else (user1)

        transaction {
            ConsensusAdminDao.new {
                this.consensus = ConsensusDao[1]
                this.user = UsersDao[2]
            }

            ConsensusAdminDao.new {
                this.consensus = ConsensusDao[2]
                this.user = UsersDao[2]
            }
        }
        list = ConsensusRepository.getConsensusAdminResponse(UsersRepository.findUserByName("User2")!!)
        println(list.toString())
        assertEquals(7, list.size)

        // we add a follower from user2 on a consensus owned by user3

        transaction {
            ConsensusFollowersDao.new {
                this.user = UsersDao[2]
                this.consensus = ConsensusDao[12]
            }
        }
        var followerList = ConsensusRepository.getConsensusFollowedResponse(UsersRepository.findUserByName("User2")!!)
        println(followerList.toString())
        assertEquals(6, followerList.size)

        // we add a suggestion from user1 on a consensus owned by user3, this should change nothing at all

        transaction {
            SuggestionsDao.new {
                this.title = "ConsensusSuggestion"
                this.creationDate = DateTime.now()
                this.creator = UsersDao[1]
                this.consensus = ConsensusDao[13]
                this.overallAcceptance = null
            }
        }
        list = ConsensusRepository.getConsensusAdminResponse(UsersRepository.findUserByName("User2")!!)
        println(list.toString())
        assertEquals(7, list.size)

        // user2 follows another one

        transaction {
            ConsensusFollowersDao.new {
                this.user = UsersDao[2]
                this.consensus = ConsensusDao[2]
            }
        }
        followerList = ConsensusRepository.getConsensusFollowedResponse(UsersRepository.findUserByName("User2")!!)
        println(followerList.toString())
        assertEquals(7, followerList.size)

        // paginated following consensuses
        val consensusResponse0 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus/following?limit=1&offset=1&finished=false",
            headers = mapOf("Authorization" to "Bearer $authHeader2")
        )

        Assert.assertEquals(1, consensusResponse0.mappedData!!.size)

        // paginated admin consensuses
        val consensusResponse1 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus/admin?limit=2&offset=1&finished=false",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(2, consensusResponse1.mappedData!!.size)

        // no more open consensuses
        val consensusResponse2 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus/admin?&offset=0&finished=true",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(0, consensusResponse2.mappedData!!.size)

        // accessor testing with user 4, should have all 4 admins
        createAuthenticatedUserToken(userName = "User4")
        (1..5).forEach {
            val body = ConsensusBody(
                "$it",
                endDate = System.currentTimeMillis() + 2000,
                privatePassword = "right",
                isPublic = false,
                votingStartDate = System.currentTimeMillis() + 500
            )
            ConsensusRepository.createResponse(body, UsersRepository.findUserByName("User4")!!)
        }
        list = ConsensusRepository.getConsensusAdminResponse(UsersRepository.findUserByName("User4")!!)
        println(list.toString())
        assertEquals(5, list.size)

        // adding suggestions and voting from people who are not accessors should
        // not be possible (also: all other manipulations, suggestions and votings ...)
        val failedPostSuggestion = postResponse<SuggestionResponse>(
            route = "v1/consensus/20/suggestions",
            postBody = SuggestionBody(title = "not important"),
            headers = mapOf("Authorization" to "Bearer $authHeader3")
        )
        Assert.assertEquals(HttpStatusCode.Forbidden, failedPostSuggestion.response?.status())
        var suggId = transaction {
            SuggestionsDao.new {
                this.title = "ConsensusSuggestion"
                this.creationDate = DateTime.now()
                this.creator = UsersDao[4]
                this.consensus = ConsensusDao[20]
                this.overallAcceptance = null
            }.id.value
        }
        val failedPostVote = postResponse<SuggestionResponse>(
            route = "v1/consensus/20/suggestions/$suggId/vote",
            postBody = VoteBody(10.0f),
            headers = mapOf("Authorization" to "Bearer $authHeader3")
        )
        Assert.assertEquals(HttpStatusCode.BadRequest, failedPostVote.response?.status())

        // fail at granting access because of wrong password
        val failedPostAccess = postResponse<ConsensusResponse>(
            route = "v1/consensus/20/requestAccess",
            postBody = RequestAccessBody("wrong"),
            headers = mapOf("Authorization" to "Bearer $authHeader3")
        )
        Assert.assertFalse(failedPostAccess.mappedData?.hasAccess!!)

        // succeed at granting access because of right password
        val successPostAccess = postResponse<ConsensusResponse>(
            route = "v1/consensus/20/requestAccess",
            postBody = RequestAccessBody("right"),
            headers = mapOf("Authorization" to "Bearer $authHeader3")
        )
        Assert.assertTrue(successPostAccess.mappedData?.hasAccess!!)

        runBlocking {
            delay(1_500)
        }

        //succeed at posting a vote, because its a successor now
        val successPostVote = postResponse<SuggestionResponse>(
            route = "v1/consensus/20/suggestions/$suggId/vote",
            postBody = VoteBody(10.0f),
            headers = mapOf("Authorization" to "Bearer $authHeader3")
        )
        Assert.assertNull(successPostVote.mappedData?.heavyObjectionsCount) // because the consensus is not finished
        Assert.assertNull(successPostVote.mappedData?.overallAcceptance) // because the consensus is not finished

        suggId = transaction {
            SuggestionsDao.new {
                this.title = "ConsensusSuggestion"
                this.creationDate = DateTime.now()
                this.creator = UsersDao[4]
                this.consensus = ConsensusDao[15]
                this.overallAcceptance = null
            }.id.value
        }

        //succeed at posting a vote, because its a successor now
        val successPostVote10 = postResponse<SuggestionResponse>(
            route = "v1/consensus/15/suggestions/$suggId/vote",
            postBody = VoteBody(10.0f),
            headers = mapOf("Authorization" to "Bearer $authHeader3")
        )
        Assert.assertNull(successPostVote10.mappedData?.heavyObjectionsCount) // because the consensus is not finished

        runBlocking {
            delay(2_500)
        }

        val heavyObjectionsCountResponse = getResponse<SuggestionResponse>(
            route = "v1/consensus/15/suggestions/$suggId/",
            headers = mapOf("Authorization" to "Bearer $authHeader3")
        )

        Assert.assertEquals(1, heavyObjectionsCountResponse.mappedData?.heavyObjectionsCount)
    }

    @Test
    fun `should test consensus filtering with pagination`() = withTestApplication(Application::module) {

        DatabaseUtil.initialize()

        authHeader1 = createAuthenticatedUserToken(userName = "User1")

        transaction {
            (1..50).forEach {
                val body = ConsensusBody(
                    "$it",
                    endDate = System.currentTimeMillis() + ((it * 1) + 1000),
                    votingStartDate = System.currentTimeMillis()
                )
                ConsensusRepository.createResponse(body, UsersRepository.findUserByName("User1")!!)
            }
        }

        // no consensus is finished (depends a bit on runtime, we only have like 1 second to generate and then the call)
        val consensusResponse1 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?limit=5&offset=0&finished=true",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertTrue(consensusResponse1.mappedData!!.isEmpty())

        // at least five consensuses are still open
        val consensusResponse2 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?limit=5&offset=0&finished=false",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(5, consensusResponse2.mappedData!!.size)

        runBlocking { delay(3000) }

        // now, there are all consensuses finished
        val consensusResponse3 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?limit=40&offset=10&finished=true",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )
        // do not get confused here, we get ids 1 .. 40 because it is sorted and we do not get the first ten entries,
        // meaning, the last 10 ids!
        Assert.assertEquals(40, consensusResponse3.mappedData!!.size)

        // no more open consensuses
        val consensusResponse4 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?limit=50&offset=0&finished=false",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(0, consensusResponse4.mappedData!!.size)

    }

    @Test
    fun `should test search`() = withTestApplication(Application::module) {
        DatabaseUtil.initialize()

        authHeader1 = createAuthenticatedUserToken(userName = "User1")

        transaction {
            val body1 = ConsensusBody("title", endDate = FAR_FUTURE, votingStartDate = FAR_FUTURE)
            ConsensusRepository.createResponse(body1, UsersRepository.findUserByName("User1")!!)
            val body2 = ConsensusBody("title and text", endDate = FAR_FUTURE, votingStartDate = FAR_FUTURE)
            ConsensusRepository.createResponse(body2, UsersRepository.findUserByName("User1")!!)
            val body3 = ConsensusBody("title and text and all", endDate = FAR_FUTURE, votingStartDate = FAR_FUTURE)
            ConsensusRepository.createResponse(body3, UsersRepository.findUserByName("User1")!!)
            val body4 = ConsensusBody("message", endDate = FAR_FUTURE, votingStartDate = FAR_FUTURE)
            ConsensusRepository.createResponse(body4, UsersRepository.findUserByName("User1")!!)

            (1..50).forEach {
                val body = ConsensusBody(
                    "title+$it",
                    endDate = System.currentTimeMillis() + ((it * 1) + 1000),
                    votingStartDate = System.currentTimeMillis()
                )
                ConsensusRepository.createResponse(body, UsersRepository.findUserByName("User1")!!)
            }
        }

        val consensusResponse = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?search=aaa",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        assertEquals(HttpStatusCode.OK, consensusResponse.response?.status())
        assertEquals(0, consensusResponse.mappedData?.size)

        val consensusResponse1 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus/?search=title",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(53, consensusResponse1.mappedData!!.size)

        val consensusResponseLimit = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?search=title&limit=30&offset=0",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(30, consensusResponseLimit.mappedData!!.size)

        val consensusResponse2 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?search=a",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(3, consensusResponse2.mappedData!!.size)

        val consensusResponse3 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?search=and",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(2, consensusResponse3.mappedData!!.size)

        val consensusResponse4 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?search=e",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(54, consensusResponse4.mappedData!!.size)

        val consensusResponse5 = getResponse<ArrayList<ConsensusResponse>>(
            "v1/consensus?search=message",
            headers = mapOf("Authorization" to "Bearer $authHeader1")
        )

        Assert.assertEquals(1, consensusResponse5.mappedData!!.size)
    }

    companion object {
        const val FAR_FUTURE = 27261948800000L
    }
}