package net.bestia.zone.jwt

import net.bestia.zone.BestiaException

class JwtLoginException(
  message: String,
  cause: Throwable? = null
) : BestiaException("VERIFY_LOGIN_FAILED", message, cause)
