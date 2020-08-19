package de.ka.rgreed.model

import io.ktor.http.HttpStatusCode
import org.apache.http.HttpException

data class ConsensusException(val httpCode: HttpStatusCode, val errors: List<Error>) : HttpException()

data class Error(
    val errorType: ErrorType,
    val parameter: String? = null
)

enum class ErrorType(val code: Int, val shortDescription: String) {
    RES_NOT_FOUND(404, "Resource not found"),
    FORBIDDEN(403, "Forbidden access"),
    BAD_REQUEST(400, "General Bad Request"),
    BAD_CREDENTIALS(401, "Bad Credentials"),
    ALREADY_TAKEN(406, "User is already taken"),
    DUPLICATED_SUGGESTION_TITLE(409, "Suggestion title already exists"),
    VOTING_START(444, "Voting start date too early"),
    TITLE_MIN(445, "Title not long enough"),
    CONSENSUS_END(446, "Consensus date invalid."),
    USERNAME_MIN(450, "Username too short"),
    EMAIL_MIN(451, "Email too short"),
    PASSWORD_MIN(452, "Password too short"),
    BAD_VOTING_RANGE(477, "Bad voting range (not 0-10)")
}


// shorter exception versions for convenient sending:
data class ApiErrorResponse(val errors: List<ApiError>)

data class ApiError(val code: Int, val description: String, val parameter: String?)