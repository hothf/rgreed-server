package de.ka.rgreed.util

import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import de.ka.rgreed.dao.model.UsersDao
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.util.pipeline.PipelineContext
import java.util.*

/**
 * Represents the jwt configuration object to create a jwt token after a successful login.
 */
object JwtAuth {

    private val auth by lazy { JwtAlgorithm("zAP5MBA4B4Ijz0MZaS48") }

    private const val validityInMs = 36_000_00 * 24 * 3 // 3 days

    val verifier = auth.verifier

    fun makeToken(user: UsersDao): String = auth.makeToken(user)

    /**
     * Calculate the expiration Date based on current time + the given validity.
     */
    fun getExpiration() = Date(System.currentTimeMillis() + validityInMs)

}

/**
 * Extension for finding the user id of the current call, if any.
 */
fun PipelineContext<Unit, ApplicationCall>.getAuthUser(): UsersDao? {
    return call.authentication.principal()
}

/**
 * Represents the jwt algorithm to build and sign a jwt login token.
 */
open class JwtAlgorithm(secret: String) {

    private val algorithm = Algorithm.HMAC512(secret)
    private val issuer = "ktor.io"

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(issuer)
        .build()

    /**
     * Produce a token for this combination of username and password.
     */
    fun makeToken(user: UsersDao): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withClaim("userName", user.userName)
        .sign(algorithm)

}