package de.ka.rgreed.dao.model

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

/**
 * Represents the suggestion vote table.
 */
object SuggestionsVoteTable : IntIdTable() {
    val suggestion = reference("suggestion", SuggestionsTable)
    val voter = reference("voter", UsersTable)
    val acceptance = float("acceptance")
}

class SuggestionsVoteDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SuggestionsVoteDao>(SuggestionsVoteTable)

    var acceptance by SuggestionsVoteTable.acceptance
    var suggestion by SuggestionsDao referencedOn SuggestionsVoteTable.suggestion
    var voter by UsersDao referencedOn SuggestionsVoteTable.voter

}