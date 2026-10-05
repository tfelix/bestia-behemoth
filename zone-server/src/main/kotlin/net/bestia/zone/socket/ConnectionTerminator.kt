package net.bestia.zone.socket

/** Ends an account's connection from the server side, for a kick or a ban. */
interface ConnectionTerminator {

  /**
   * Tells the client [reason] and closes its connection. The account is then released the same way as on any
   * other disconnect. Answers whether the account had a connection here.
   */
  fun disconnect(accountId: Long, reason: String): Boolean
}
