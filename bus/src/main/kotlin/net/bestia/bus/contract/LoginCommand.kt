package net.bestia.bus.contract

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * A GM action the api-server asks the login server to carry out, because the login server owns the accounts. The
 * acting GM travels on the [LoginRequest], so the login server checks its authority on every command.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed interface LoginCommand {

  val targetAccountId: Long

  data class Kick(override val targetAccountId: Long, val note: String) : LoginCommand

  /** A null [durationHours] bans for good. */
  data class Ban(override val targetAccountId: Long, val note: String, val durationHours: Long?) : LoginCommand
}
