package net.bestia.login.zone

/** A zone server, as the login server calls it. [baseUrl] is its HTTP port, not the game socket. */
data class ZoneEndpoint(
  val name: String,
  val baseUrl: String
)
