package net.bestia.loginserver.account

data class CreateBasicLoginRequestV1(
    val email: String,
    val password: String
)