package de.ka.rgreed.route.v1

import de.ka.rgreed.dao.UsersRepository
import de.ka.rgreed.dao.UsersRepository.EMAIL_MIN_LENGTH
import de.ka.rgreed.dao.UsersRepository.PASSWORD_MIN_LENGTH
import de.ka.rgreed.dao.UsersRepository.USERNAME_MIN_LENGTH
import de.ka.rgreed.model.*
import de.ka.rgreed.model.LoginBody
import de.ka.rgreed.model.PushTokenBody
import de.ka.rgreed.model.RegisterBody
import de.ka.rgreed.util.HashUtil
import de.ka.rgreed.util.getAuthUser
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post

/**
 * Represents the login and register [Route].
 */
fun Route.routeLoginV1() {

    /**
     * A public login [Route] used to obtain JWTs.
     */
    post {
        val loginBody = call.receive<LoginBody>()
        val storedUser = UsersRepository.findUserByName(loginBody.userName)
        if (storedUser != null && storedUser.passwordHash == HashUtil.hashString(loginBody.password)) {
            UsersRepository.handlePushToken(loginBody.pushToken, storedUser)
            call.respond(UsersRepository.loginResponse(storedUser))
        } else {
            throw ConsensusException(HttpStatusCode.BadRequest, listOf(Error(ErrorType.BAD_CREDENTIALS, "password")))
        }
    }
}

fun Route.routeRegisterLoginV1() {
    /**
     * A public register [Route] used create a new user and immediately return a login.
     */
    post("/") {
        val postUser = call.receive<RegisterBody>()
        val errorList = mutableListOf<Error>()
        if (postUser.userName.length < USERNAME_MIN_LENGTH) {
            errorList.add(Error(ErrorType.USERNAME_MIN, "username"))
        }
        if (postUser.email.length < EMAIL_MIN_LENGTH) {
            errorList.add(Error(ErrorType.EMAIL_MIN, "email"))
        }
        if (postUser.password.length < PASSWORD_MIN_LENGTH) {
            errorList.add(Error(ErrorType.PASSWORD_MIN, "password"))
        }
        if (errorList.isNotEmpty()) {
            throw ConsensusException(HttpStatusCode.BadRequest, errorList)
        }

        val storedUser = UsersRepository.findUserByName(postUser.userName)
        if (storedUser == null) {
            call.respond(HttpStatusCode.Created, UsersRepository.createResponse(postUser))
        } else {
            throw ConsensusException(HttpStatusCode.Conflict, listOf(Error(ErrorType.ALREADY_TAKEN, "username")))
        }
    }

    authenticate {
        post("/push") {
            val pushBody = call.receive<PushTokenBody>()

            getAuthUser()?.let {
                UsersRepository.handlePushToken(pushBody.pushToken, it)
                call.respond(HttpStatusCode.OK)
            }
        }
    }

}