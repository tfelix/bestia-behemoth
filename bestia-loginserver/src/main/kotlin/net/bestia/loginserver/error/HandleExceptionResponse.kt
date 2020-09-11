package net.bestia.loginserver.error

import org.springframework.http.ResponseEntity

internal fun handleExceptionResponse(ex: BestiaException): ResponseEntity<BestiaErrorMessage> {
  val errorMessage = BestiaErrorMessage(
      errorCode = ex.errorCode,
      errorMessage = ex.message ?: ""
  )

  return ResponseEntity
      .status(ex.httpCode)
      .body(errorMessage)
}