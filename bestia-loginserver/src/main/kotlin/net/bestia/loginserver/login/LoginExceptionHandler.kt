package net.bestia.loginserver.login

import net.bestia.loginserver.error.BestiaErrorMessage
import net.bestia.loginserver.error.BestiaHttpException
import net.bestia.loginserver.error.handleExceptionResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@ControllerAdvice(basePackageClasses = [LoginController::class])
class LoginExceptionHandler : ResponseEntityExceptionHandler() {

  @ExceptionHandler(value = [BestiaHttpException::class])
  fun handleConflict(ex: BestiaHttpException): ResponseEntity<BestiaErrorMessage> {
    return handleExceptionResponse(ex)
  }
}