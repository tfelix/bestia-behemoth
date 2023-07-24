package net.bestia.loginserver.login

import net.bestia.loginserver.error.BestiaHttpException
import net.bestia.loginserver.error.BestiaError
import org.springframework.http.HttpStatus
import java.time.ZonedDateTime

class BannedAccountException(until: ZonedDateTime) : BestiaHttpException(
    httpCode = HttpStatus.FORBIDDEN,
    errorCode = BestiaError.AUTH_ACCOUNT_BANNED
) {
  override val message = until.toString()
}