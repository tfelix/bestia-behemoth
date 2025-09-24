package net.bestia.login

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/login")
class LoginController(
  private val loginService: LoginService
) {

  data class LoginRequest(
    val refreshToken: String
  )

  enum class LoginErrorCode {
    ACCOUNT_NOT_FOUND,
    NFT_OWNERSHIP_VERIFICATION_FAILED,
    INTERNAL_ERROR,
    GENERAL_ERROR
  }

  data class LoginSuccess(
    val token: String
  )

  data class LoginError(
    val error: LoginErrorCode,
    val message: String?
  )

  @PostMapping
  fun login(@RequestBody request: LoginRequest): ResponseEntity<*> {
    return try {
      val result = loginService.login(request.refreshToken)
      ResponseEntity.ok(
        LoginSuccess(
          token = result,
        )
      )
    } catch (e: AccountNotFoundException) {
      ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(LoginError(LoginErrorCode.ACCOUNT_NOT_FOUND, e.message))
    } catch (e: NftOwnershipVerificationFailedException) {
      ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(LoginError(LoginErrorCode.NFT_OWNERSHIP_VERIFICATION_FAILED, e.message))
    } catch (e: InternalLoginException) {
      ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(LoginError(LoginErrorCode.INTERNAL_ERROR, e.message))
    } catch (_: Exception) {
      ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(LoginError(LoginErrorCode.GENERAL_ERROR, "An unexpected error occurred"))
    }
  }
}