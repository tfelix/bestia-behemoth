package net.bestia.loginserver.error

import org.springframework.http.HttpStatus

object BadRequestException : BestiaException(
    httpCode = HttpStatus.BAD_REQUEST,
    errorCode = BestiaError.BAD_REQUEST
)