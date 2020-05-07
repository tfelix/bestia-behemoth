package net.bestia.loginserver.error

open class BestiaException(
    val code: BestiaExceptionCode,
    open val extra: String = "",
    cause: Throwable? = null
) : Exception(cause = cause)