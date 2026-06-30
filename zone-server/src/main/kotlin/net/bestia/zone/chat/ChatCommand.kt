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
    return playerAuthorities.contains(requiredAuthority)
  }

  abstract fun getHelpText(): String
  abstract val requiredAuthority: Authority

  protected abstract fun isMatch(cmdText: String): Boolean
  protected abstract fun execute(playerId: Long, cmdText: String): Boolean
}

