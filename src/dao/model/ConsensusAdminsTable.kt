package de.ka.rgreed.dao.model

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

/**
 * Represents the admins table.
 */
object ConsensusAdminTable : IntIdTable() {
    val user = reference("user", UsersTable)
    val consensus = reference("consensus", ConsensusTable)
}

class ConsensusAdminDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ConsensusAdminDao>(ConsensusAdminTable)

    var user by UsersDao referencedOn ConsensusAdminTable.user
    var consensus by ConsensusDao referencedOn ConsensusAdminTable.consensus
}