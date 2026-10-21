package net.bestia.internal

/**
 * The companion app's access token: minted by the login server, checked by the api-service. Signed with a key of
 * its own (`jwt.companion-secret`), so neither the token nor the key that checks it can open a zone.
 */
object CompanionTokens {
  const val ISSUER = "login"
  const val AUDIENCE = "companion"
}
