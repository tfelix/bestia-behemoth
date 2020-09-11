package net.bestia.loginserver.login

import net.bestia.loginserver.error.BestiaException
import net.bestia.loginserver.error.BestiaError
import org.springframework.http.HttpStatus
import java.time.ZonedDateTime

class BannedAuthenticationException(until: ZonedDateTime) : BestiaException(
    httpCode = HttpStatus.FORBIDDEN,
    errorCode = BestiaError.AUTH_ACCOUNT_BANNED
) {
  override val message = until.toString()
}