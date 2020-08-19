package de.ka.rgreed.dao

import de.ka.rgreed.dao.model.UsersDao
import de.ka.rgreed.dao.model.UsersTable
import de.ka.rgreed.model.RegisterBody
import de.ka.rgreed.model.UserResponse
import de.ka.rgreed.util.HashUtil
import de.ka.rgreed.util.JwtAuth
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Represents the users repository class, which does all database operations.
 */
object UsersRepository {

    const val USERNAME_MIN_LENGTH = 3
    const val EMAIL_MIN_LENGTH = 5
    const val PASSWORD_MIN_LENGTH = 4

    /**
     * Finds a user by the given name.
     */
    fun findUserByName(userName: String): UsersDao? = transaction {
        UsersDao.find { UsersTable.userName.eq(userName) }.firstOrNull()
    }

    /**
     * Inserts a new user.
     */
    fun createResponse(item: RegisterBody): UserResponse {
        val newUser = transaction {
            deleteIfOtherUserAssigned(item.pushToken)
            UsersDao.new {
                email = item.email
                userName = item.userName
                passwordHash = HashUtil.hashString(item.password)
                pushToken = item.pushToken
            }
        }

        return loginResponse(newUser)
    }

    fun handlePushToken(token: String?, user: UsersDao) {
        if (user.pushToken == null || !user.pushToken.equals(token)) {
            handlePushTokenStorage(token, user)
        }
    }

    /**
     * Retrieves a login response for a given user.
     */
    fun loginResponse(user: UsersDao) = UserResponse(user.id.value, user.userName, user.email, JwtAuth.makeToken(user))

    /**
     * Retrieves all users.
     */
    fun getAllResponse(): List<UserResponse> {
        return transaction { UsersDao.all().map { it.toResponse() } }
    }

    /**
     * Handles the storage of the device token.
     */
    private fun handlePushTokenStorage(token: String?, user: UsersDao) {
        if (token == null) {
            return
        } else {
            return transaction {
                deleteIfOtherUserAssigned(token)
                user.apply { this.pushToken = token }
            }
        }
    }

    /**
     * Find and delete the token if its already assigned to another user.
     * This ensures, that every push token is just assigned once per user
     *
     * This method has to be called inside a TRANSACTION!!.
     */
    private fun deleteIfOtherUserAssigned(pushToken: String?) {
        UsersDao.find { UsersTable.pushToken eq pushToken }.firstOrNull()?.apply {
            this.pushToken = null
        }
    }

    /**
     * Converts a user dao to a user response.
     */
    private fun UsersDao.toResponse(): UserResponse {
        return UserResponse(
            id = this.id.value,
            email = this.email,
            userName = this.userName
        )
    }
}