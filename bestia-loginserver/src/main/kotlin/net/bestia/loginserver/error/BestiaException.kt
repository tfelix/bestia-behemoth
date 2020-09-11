package net.bestia.loginserver.error

import org.springframework.http.HttpStatus

open class BestiaException(
    val httpCode: HttpStatus,
    val errorCode: BestiaError,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)