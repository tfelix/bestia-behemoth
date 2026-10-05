package net.bestia.internal

/**
 * The contract of the calls the login server makes to a zone. The login server signs these tokens and the zone
 * checks them, so both read the names from here.
 */
object ServiceTokens {

  const val ISSUER = "login"

  /** Not the `zone` audience of a login token, so neither kind of token can stand in for the other. */
  const val AUDIENCE = "zone-internal"

  /** The one account the call may act on. */
  const val ACCOUNT_CLAIM = "acc"

  /** The one thing the call may do to it. */
  const val SCOPE_CLAIM = "scope"

  const val KICK_SCOPE = "kick"

  /** Long enough for one call, short enough that a token found in a log is already useless. */
  const val LIFETIME_SECONDS = 30L

  const val KICK_PATH = "/internal/v1/accounts/{accountId}/kick"

  fun kickPath(accountId: Long): String {
    return KICK_PATH.replace("{accountId}", accountId.toString())
  }
}
