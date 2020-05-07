package net.bestia.loginserver.login

import net.bestia.loginserver.error.BestiaException
import net.bestia.loginserver.error.BestiaExceptionCode
import java.time.ZonedDateTime

class BannedAuthenticationException(until: ZonedDateTime) : BestiaException(BestiaExceptionCode.AUTH_FAILED) {
  override val extra = until.toString()
}