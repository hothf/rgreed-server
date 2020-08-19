package de.ka.rgreed.dao.model

import io.ktor.auth.Principal
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

/**
 * Represents the UsersTable table using Exposed as DAO.
 */
object UsersTable : IntIdTable(), Principal {
    val email = varchar("email", 128).uniqueIndex()
    val userName = varchar("username", 256).uniqueIndex()
    val passwordHash = varchar("password_hash", 64)
    val pushToken = varchar("pushToken", 256).nullable()
}

class UsersDao(id: EntityID<Int>) : IntEntity(id), Principal {
    companion object : IntEntityClass<UsersDao>(UsersTable), Principal

    var email by UsersTable.email
    var userName by UsersTable.userName
    var passwordHash by UsersTable.passwordHash
    var pushToken by UsersTable.pushToken
}