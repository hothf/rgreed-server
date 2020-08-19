package de.ka.rgreed.dao.model

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

/**
 * Represents the followers table.
 */
object ConsensusFollowersTable : IntIdTable() {
    val user = reference("user", UsersTable)
    val consensus = reference("consensus", ConsensusTable)
}

class ConsensusFollowersDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ConsensusFollowersDao>(ConsensusFollowersTable)

    var user by UsersDao referencedOn ConsensusFollowersTable.user
    var consensus by ConsensusDao referencedOn ConsensusFollowersTable.consensus
}