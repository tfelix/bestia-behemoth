package net.bestia.login.jwt

import net.bestia.login.LoginException

class JwtRefreshException(
  val error: String
) : LoginException("INVALID_REFRESH_TOKEN")