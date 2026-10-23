package net.bestia.zone.account.authentication

import net.bestia.zone.BestiaException

class JwtLoginException(
  message: String,
  cause: Throwable? = null,
  /** What went wrong, as a short fixed list the metrics can count. */
  val reason: AuthMetrics.Refusal = AuthMetrics.Refusal.INVALID_TOKEN,
) : BestiaException("VERIFY_LOGIN_FAILED", message, cause)