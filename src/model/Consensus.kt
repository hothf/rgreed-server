package de.ka.rgreed.model

data class ConsensusResponse(
    val id: Int,
    val title: String,
    val admin: Boolean = false,
    val suggestionsCount: Int,
    val public: Boolean,
    val creator: String,
    val creationDate: Long,
    val description: String? = null,
    val endDate: Long,
    val votingStartDate: Long,
    val voters: List<String> = listOf(),
    val finished: Boolean,
    val hasAccess: Boolean,
    val following: Boolean = false
)

data class ConsensusBody(
    val title: String,
    val description: String? = null,
    val isPublic: Boolean = true,
    val endDate: Long,
    val votingStartDate: Long,
    val privatePassword: String = ""
)

data class RequestAccessBody(val password: String)

data class FollowBody(val follow: Boolean)