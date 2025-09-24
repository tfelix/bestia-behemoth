package net.bestia.login.eip712

data class Eip712AuthRequest(
    val wallet: String,
    val tokenIndex: Long,
    val signature: String,
)