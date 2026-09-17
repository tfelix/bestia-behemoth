package net.bestia.zone.socket

import net.bestia.zone.message.SMSG

interface OutMessageHandler {
  fun sendMessage(playerId: Long, outMessage: SMSG)

  /**
   * Sends several messages to one account as a single flush.
   *
   * The distinction matters at the socket: [sendMessage] flushes per message, so a tick's worth of
   * component updates for one entity was one syscall each. Nearly every send this server makes is a
   * batch already - [net.bestia.zone.ecs.ZoneEngine] assembles all of an entity's dirty components
   * before handing them over - so the batch just needs to survive the trip rather than be unrolled
   * on arrival. This is what eAthena's per-session WFIFO plus one `socket_flush` per loop does.
   *
   * Defaulted to the unrolled loop so the test doubles that implement this interface need no
   * batching of their own; only [ChannelRegistry] has a flush to save.
   */
  fun sendMessages(playerId: Long, outMessages: Collection<SMSG>) {
    outMessages.forEach { sendMessage(playerId, it) }
  }

  /**
   * Every account with a live connection, for the rare message addressed to the world rather than to a
   * player - a world-clock jump, and nothing else today.
   *
   * Defaulted to empty rather than abstract, because the test doubles that implement this interface stand in
   * for one client each and have no notion of who else is online. A broadcast through one of them reaching
   * nobody is the correct answer, not a gap.
   */
  val connectedAccountIds: Set<Long> get() = emptySet()

  /**
   * Whether [playerId] can be reached from here at all.
   *
   * Sending to an absent account is already a silent drop, which is the right behaviour for state the
   * client will be told again anyway. It is the wrong answer for a one-off a player is waiting on - a
   * whisper is gone for good - so those callers ask first and say so.
   *
   * Defaulted off [connectedAccountIds] rather than left abstract, so a test double that answers one
   * answers both.
   */
  fun isConnected(playerId: Long): Boolean = playerId in connectedAccountIds
}
