package net.bestia.loginserver.error

import net.bestia.loginserver.error.BestiaError

data class BestiaErrorMessage(
    val errorMessage: String,
    val errorCode: BestiaError
)