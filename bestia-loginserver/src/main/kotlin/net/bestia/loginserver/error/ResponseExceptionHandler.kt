package net.bestia.loginserver.error

import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import java.lang.IllegalArgumentException


@ControllerAdvice
class ResponseExceptionHandler : ResponseEntityExceptionHandler() {

  private data class Error(
      val errorMsg: String
  )

  @ExceptionHandler(value = [IllegalArgumentException::class])
  fun handleConflict(ex: RuntimeException, request: WebRequest): ResponseEntity<Any> {
    // Build a proper exception
    val bodyOfResponse = "This should be application specific"
    return handleExceptionInternal(
        ex,
        Error(bodyOfResponse),
        HttpHeaders(),
        HttpStatus.CONFLICT,
        request
    )
  }

  @ExceptionHandler(value = [Exception::class])
  protected fun handleGeneral(ex: RuntimeException, request: WebRequest): ResponseEntity<Any> {
    // Build a proper exception
    val bodyOfResponse = "General Error"
    return handleExceptionInternal(
        ex,
        Error(bodyOfResponse),
        HttpHeaders(),
        HttpStatus.CONFLICT,
        request
    )
  }
}