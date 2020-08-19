
import de.ka.rgreed.dao.UsersRepository
import de.ka.rgreed.model.LoginBody
import de.ka.rgreed.model.RegisterBody
import de.ka.rgreed.model.UserResponse
import de.ka.rgreed.module
import io.ktor.application.Application
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Test
import util.DatabaseUtil

import kotlin.test.*
import util.postResponse

/**
 * Intended for testing registration calls and responses.
 */
class RegisterTest {

    @Test
    fun `should register + login correctly once`() = withTestApplication(Application::module) {

        DatabaseUtil.initialize()

        // successful creation
        val registerResponseCreate = postResponse<Any>(
            "v1/register",
            RegisterBody(userName = "hello", email = "halo@blubb.com", password = "daokdmo"),
            print = true
        )

        runBlocking {
            // response result evaluation
            assertEquals(HttpStatusCode.Created, registerResponseCreate.response?.status())

            // database result evaluation
            assertEquals(1, UsersRepository.getAllResponse().count())
            assertEquals("hello", UsersRepository.findUserByName("hello")?.userName)
        }

        // already taken
        val registerResponseAlreadyTaken = postResponse<Any>(
            "v1/register",
            RegisterBody(userName = "hello", email = "halo@blubb.com", password = "daokdmo"),
            print = true
        )

        // response result evaluation
        assertEquals(HttpStatusCode.Conflict, registerResponseAlreadyTaken.response?.status())

        // invalid user credentials
        val registerResponseUsernameShort = postResponse<Any>(
            "v1/register",
            RegisterBody(userName = "al", email = "halo", password = "daokdmo"),
            print = true
        )

        // response result evaluation
        assertEquals(HttpStatusCode.BadRequest, registerResponseUsernameShort.response?.status())

        // invalid user credentials
        val registerResponseEmailShort = postResponse<Any>(
            "v1/register",
            RegisterBody(userName = "alfo", email = "a@b.", password = "daokdmo"),
            print = true
        )

        // response result evaluation
        assertEquals(HttpStatusCode.BadRequest, registerResponseEmailShort.response?.status())

        // invalid user credentials
        val registerResponsePasswordShort = postResponse<Any>(
            "v1/register",
            RegisterBody(userName = "alfen", email = "a@b.c", password = "dao"),
            print = true
        )

        // response result evaluation
        assertEquals(HttpStatusCode.BadRequest, registerResponsePasswordShort.response?.status())

        // login
        val loginResponseSuccess = postResponse<UserResponse>(
            "v1/login",
            LoginBody("hello", "daokdmo"),
            print = true
        )

        // response result evaluation
        val token = loginResponseSuccess.mappedData!!.token

        assertEquals(HttpStatusCode.OK, loginResponseSuccess.response?.status())
        assertTrue(token?.startsWith("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9")!!)
    }
}