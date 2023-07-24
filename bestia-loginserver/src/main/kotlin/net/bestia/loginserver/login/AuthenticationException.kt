package net.bestia.loginserver.login

import net.bestia.loginserver.error.BestiaError
import net.bestia.loginserver.error.BestiaHttpException
import org.springframework.http.HttpStatus

object AuthenticationException : BestiaHttpException(
    httpCode = HttpStatus.FORBIDDEN,
    errorCode = BestiaError.AUTH_FAILED
)