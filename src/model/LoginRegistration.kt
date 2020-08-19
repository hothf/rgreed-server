package de.ka.rgreed.model

data class RegisterBody(
    val userName: String,
    val email: String,
    val password: String,
    val pushToken: String? = null
)

data class UserResponse(
    val id: Int? = null,
    val userName: String,
    val email: String,
    val token: String? = null
)

data class LoginBody(
    val userName: String,
    val password: String,
    val pushToken: String? = null
)

data class PushTokenBody(
    val pushToken: String
)