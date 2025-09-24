package net.bestia.login.eip712

sealed class Eip712AuthResponse {
    data class Success(
        val wallet: String,
        val tokenIndex: Long,
        val token: String
    ) : Eip712AuthResponse()

    data class Failure(
        val error: String
    ) : Eip712AuthResponse()
}