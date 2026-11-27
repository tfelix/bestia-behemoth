package net.bestia.bus.contract

/**
 * One [LoginCommand] on its way to the login server.
 *
 * [actorAccountId] is the GM as the api-server authenticated it. The login server trusts it because only the
 * api-server may publish login commands (Valkey ACL).
 *
 * @property replyTo the channel of the api-server instance that is waiting
 * @property deadlineEpochMs past this the caller has given up, so the login server does not act any more
 */
data class LoginRequest(
  val correlationId: String,
  val replyTo: String,
  val deadlineEpochMs: Long,
  val actorAccountId: Long,
  val command: LoginCommand,
)
