package net.bestia.zone.account.authentication

import net.bestia.zone.BestiaException

class JwtLoginException(
  message: String,
  cause: Throwable? = null
) : BestiaException("VERIFY_LOGIN_FAILED", message, cause)