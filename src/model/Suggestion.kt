package de.ka.rgreed.model

data class SuggestionBody(
    val title: String
)

data class SuggestionResponse(
    val id: Int,
    val title: String,
    val consensusId: Int,
    val overallAcceptance: Float? = null,
    val creationDate: Long,
    val admin: Boolean = false,
    val ownAcceptance: Float? = null,
    val voters: List<String> = listOf(),
    val heavyObjectionsCount: Int? = null
)

data class VoteBody(val acceptance: Float)