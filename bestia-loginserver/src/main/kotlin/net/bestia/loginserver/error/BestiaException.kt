package net.bestia.loginserver.error

import net.bestia.loginserver.error.BestiaExceptionCode

abstract class BestiaException(
    val code: BestiaExceptionCode,
    open val extra: String = ""
) : Exception()