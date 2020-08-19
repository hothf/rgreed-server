package util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.http.HttpMethod
import com.fasterxml.jackson.module.kotlin.readValue
import de.ka.rgreed.dao.UsersRepository
import de.ka.rgreed.model.RegisterBody
import de.ka.rgreed.util.JwtAuth
import io.ktor.server.testing.*
import java.lang.Exception

/**
 * Tries to get the response of the given post object and converts it to the reified type in a data field
 * of a [TestResponse].
 * Uses jackson parsing for converting it to json in the process of sending and converting
 * back to the wished result. If the conversion fails, the data object inside the [TestResponse] will remain
 * null.
 *
 * There is an additional field inside the [TestResponse] named response which may contain the unparsed
 * result, if the call could made it through.
 *
 * @param route the route to accept the post body
 * @param postBody the post body object
 * @param headers optional map of headers, defaults to using none
 * @param print optional, if set, will print the mapped result, if possible. Defaults to true
 * @return the result object, containing mapped data and the complete response
 */
inline fun <reified T : Any> TestApplicationEngine.postResponse(
    route: String,
    postBody: Any,
    headers: Map<String, String> = mapOf(),
    print: Boolean = true
): TestResponse<T> {

    return buildTestResponse(from = handleRequest(HttpMethod.Post, route) {

        val mapper = ObjectMapper()
        mapper.registerModule(KotlinModule())

        headers.entries.forEach { entry ->
            addHeader(entry.key, entry.value)
        }
        setBody(mapper.writeValueAsString(postBody))
    }, print = print)
}

/**
 * Tries to get the response of the given put object and converts it to the reified type in a data field
 * of a [TestResponse].
 * Uses jackson parsing for converting it to json in the process of sending and converting
 * back to the wished result. If the conversion fails, the data object inside the [TestResponse] will remain
 * null.
 *
 * There is an additional field inside the [TestResponse] named response which may contain the unparsed
 * result, if the call could made it through.
 *
 * @param route the route to accept the put body
 * @param putBody the put body object
 * @param headers optional map of headers, defaults to using none
 * @param print optional, if set, will print the mapped result, if possible. Defaults to true
 * @return the result object, containing mapped data and the complete response
 */
inline fun <reified T : Any> TestApplicationEngine.putResponse(
    route: String,
    putBody: Any,
    headers: Map<String, String> = mapOf(),
    print: Boolean = true
): TestResponse<T> {

    val mapper = ObjectMapper()
    mapper.registerModule(KotlinModule())

    return buildTestResponse(from = handleRequest(HttpMethod.Put, route) {
        headers.entries.forEach { entry ->
            addHeader(entry.key, entry.value)
        }
        setBody(mapper.writeValueAsString(putBody))
    }, print = print)
}

/**
 * Tries to get the response of the given get object and converts it to the reified type in a data field
 * of a [TestResponse].
 * Uses jackson parsing for converting it to json in the process of sending and converting
 * back to the wished result. If the conversion fails, the data object inside the [TestResponse] will remain
 * null.
 *
 * There is an additional field inside the [TestResponse] named response which may contain the unparsed
 * result, if the call could made it through.
 *
 * @param route the route to accept the get object
 * @param headers optional map of headers, defaults to using none
 * @param print optional, if set, will print the mapped result, if possible. Defaults to true
 * @return the result object, containing mapped data and the complete response
 */
inline fun <reified T : Any> TestApplicationEngine.getResponse(
    route: String,
    headers: Map<String, String> = mapOf(),
    print: Boolean = true
): TestResponse<T> {

    return buildTestResponse(from = handleRequest(HttpMethod.Get, route) {
        headers.entries.forEach { entry ->
            addHeader(entry.key, entry.value)
        }
    }, print = print)
}

/**
 * Tries to get the response of the given delete object and converts it to the reified type in a data field
 * of a [TestResponse].
 * Uses jackson parsing for converting it to json in the process of sending and converting
 * back to the wished result. If the conversion fails, the data object inside the [TestResponse] will remain
 * null.
 *
 * There is an additional field inside the [TestResponse] named response which may contain the unparsed
 * result, if the call could made it through.
 *
 * @param route the route to accept the delete object
 * @param headers optional map of headers, defaults to using none
 * @param print optional, if set, will print the mapped result, if possible. Defaults to true
 * @return the result object, containing mapped data and the complete response
 */
inline fun <reified T : Any> TestApplicationEngine.deleteResponse(
    route: String,
    headers: Map<String, String> = mapOf(),
    print: Boolean = true
): TestResponse<T> {

    return buildTestResponse(from = handleRequest(HttpMethod.Delete, route) {
        headers.entries.forEach { entry ->
            addHeader(entry.key, entry.value)
        }
    }, print = print)
}

/**
 * Builds a test response from the given test application call.
 *
 * @param from the test application call to build the response from
 * @param print optional, if set, will print the mapped result, if possible. Defaults to false
 */
inline fun <reified T : Any> buildTestResponse(from: TestApplicationCall, print: Boolean = false): TestResponse<T> {
    val testResponse = TestResponse<T>(response = from.response)

    if (from.response.content != null) {

        try {

            val mapper = ObjectMapper()
            mapper.registerModule(KotlinModule())

            val mappedResponse: T = mapper.readValue(from.response.content!!)

            if (print) {
                println("Mapped to: $mappedResponse")
            }

            testResponse.mappedData = mappedResponse
        } catch (e: Exception) {
            print("Exception during mapping: ${e.localizedMessage}")
        }
    }

    return testResponse
}

/**
 * A class containing possible info of a test call, made with a [TestApplicationEngine.handleRequest].
 * May contain data but is not guaranteed to offer it in cases of errors.
 *
 * @param mappedData mapped data, if any
 * @param response the complete response, if any
 */
data class TestResponse<T>(var mappedData: T? = null, val response: TestApplicationResponse? = null)

/**
 * Creates an authenticated user token.
 *
 * @param userName set to a unique name
 */
fun TestApplicationEngine.createAuthenticatedUserToken(userName: String): String {

    UsersRepository.createResponse(
        RegisterBody(
            email = userName,
            userName = userName,
            password = "notImportant",
            pushToken = null
        )
    )

    return JwtAuth.makeToken(UsersRepository.findUserByName(userName)!!)
}