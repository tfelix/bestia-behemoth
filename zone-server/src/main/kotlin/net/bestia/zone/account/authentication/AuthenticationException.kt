package net.bestia.zone.account.authentication

import net.bestia.zone.BestiaException

class AuthenticationException : BestiaException(
  code = "AUTH_FAILED",
  message = "Authentication was not successful"
)