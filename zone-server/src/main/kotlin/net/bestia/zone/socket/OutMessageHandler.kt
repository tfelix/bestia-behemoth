package net.bestia.zone.socket

import net.bestia.zone.message.SMSG

interface OutMessageHandler {
  fun sendMessage(playerId: Long, outMessage: SMSG)

  /**
   * Every account with a live connection, for the rare message addressed to the world rather than to a
   * player - a world-clock jump, and nothing else today.
   *
   * Defaulted to empty rather than abstract, because the test doubles that implement this interface stand in
   * for one client each and have no notion of who else is online. A broadcast through one of them reaching
   * nobody is the correct answer, not a gap.
   */
  val connectedAccountIds: Set<Long> get() = emptySet()
}