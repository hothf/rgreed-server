package de.ka.rgreed.dao.model

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

/**
 * Represents the suggestions table.
 */
object SuggestionsTable : IntIdTable() {
    val creator = reference("creator", UsersTable)
    val overallAcceptance = float("overall_acceptance").nullable()
    val title = varchar("title", 1024)
    val creationDate = datetime("creation_date")
    val consensus = reference("consensus", ConsensusTable)

}

class SuggestionsDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SuggestionsDao>(SuggestionsTable)

    var overallAcceptance by SuggestionsTable.overallAcceptance
    var title by SuggestionsTable.title
    var creationDate by SuggestionsTable.creationDate
    var creator by UsersDao referencedOn SuggestionsTable.creator
    var consensus by ConsensusDao referencedOn SuggestionsTable.consensus

    val votes by SuggestionsVoteDao referrersOn SuggestionsVoteTable.suggestion
}