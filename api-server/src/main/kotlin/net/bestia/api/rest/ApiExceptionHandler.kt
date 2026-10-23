package net.bestia.api.rest

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(BusyZoneException::class)
  fun busy(): ResponseEntity<Void> {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS).build()
  }

  private companion object {
    const val RETRY_AFTER_SECONDS = "2"
  }
}
