package net.bestia.zone.chat

import net.bestia.account.Authority

abstract class ChatCommand {
  fun tryExecute(playerId: Long, cmdText: String, playerAuthorities: Set<Authority>): Boolean {
    if (!isAvailable(playerAuthorities)) {
      return false
    }

    if (!isMatch(cmdText)) {
      return false
    }

    return execute(playerId, cmdText)
  }

  fun isAvailable(playerAuthorities: Set<Authority>): Boolean {
    val required = requiredAuthority ?: return true
    return playerAuthorities.contains(required)
  }

  abstract fun getHelpText(): String

  /** Authority needed to use this command, or null if every player may use it. */
  open val requiredAuthority: Authority? = null

  /**
   * Whether this is the command the player typed, regardless of whether they may use it.
   *
   * Public so [ChatCommandHandler] can tell "no such command" from "not yours to use" once nothing has run -
   * [tryExecute] answers `false` to both, and the two need different wording.
   */
  abstract fun isMatch(cmdText: String): Boolean
  protected abstract fun execute(playerId: Long, cmdText: String): Boolean
}

