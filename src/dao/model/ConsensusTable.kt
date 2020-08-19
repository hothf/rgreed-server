package de.ka.rgreed.dao.model

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

/**
 * Represents the MessagesDao table using Exposed as DAO.
 */
object ConsensusTable : IntIdTable() {
    val title = varchar("title", 1024)
    val creationDate = datetime("creation_date")
    val endDate = datetime("end_date")
    val votingStartDate = datetime("voting_start_date")
    val creator = reference("creator", UsersTable)
    val description = varchar("description", 1024)
    val isPublic = bool("isPublic")
    val isFinished = bool("isFinished")
    val privatePassword = varchar("private_pw", 1024)
}

class ConsensusDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ConsensusDao>(ConsensusTable)

    var title by ConsensusTable.title
    var creationDate by ConsensusTable.creationDate
    var endDate by ConsensusTable.endDate
    var creator by UsersDao referencedOn ConsensusTable.creator
    var votingStartDate by ConsensusTable.votingStartDate
    var description by ConsensusTable.description
    var isPublic by ConsensusTable.isPublic
    var isFinished by ConsensusTable.isFinished
    var privatePassword by ConsensusTable.privatePassword

    val admins by ConsensusAdminDao referrersOn ConsensusAdminTable.consensus
    val suggestions by SuggestionsDao referrersOn SuggestionsTable.consensus
    val accessors by ConsensusAccessorDao referrersOn ConsensusAccessorsTable.consensus
    val followers by ConsensusFollowersDao referrersOn ConsensusFollowersTable.consensus
}