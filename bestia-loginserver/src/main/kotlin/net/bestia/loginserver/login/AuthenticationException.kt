package net.bestia.loginserver.login

import net.bestia.loginserver.error.BestiaError
import net.bestia.loginserver.error.BestiaException
import org.springframework.http.HttpStatus

object AuthenticationException : BestiaException(
    httpCode = HttpStatus.FORBIDDEN,
    errorCode = BestiaError.AUTH_FAILED
)