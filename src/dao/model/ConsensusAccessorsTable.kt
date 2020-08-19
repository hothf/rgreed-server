package de.ka.rgreed.dao.model

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

/**
 * Represents the consensus accessors table, containing references to a user with access to a consensus.
 * Useful for private consensuses.
 */
object ConsensusAccessorsTable : IntIdTable() {
    val user = reference("user", UsersTable)
    val consensus = reference("consensus", ConsensusTable)
}

class ConsensusAccessorDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ConsensusAccessorDao>(ConsensusAccessorsTable)

    var user by UsersDao referencedOn ConsensusAccessorsTable.user
    var consensus by ConsensusDao referencedOn ConsensusAccessorsTable.consensus
}