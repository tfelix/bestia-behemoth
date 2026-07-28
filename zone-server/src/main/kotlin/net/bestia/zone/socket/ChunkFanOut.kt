package net.bestia.zone.socket

import net.bestia.zone.message.SMSG

/**
 * Delivers one message to many accounts, serialising it **exactly once**.
 *
 * The ordinary send path goes through [OutMessageHandler.sendMessage], which serialises per recipient. That
 * is the right shape for the small messages it was written for and the wrong one for chunks: thirty players
 * standing together while one of them digs would mean thirty serialisations of the same patch, and thirty
 * of the same three-kilobyte payload when someone walks into a new area.
 *
 * The contract is the serialisation count, not merely the delivery, because that is the property the design
 * rests on and the one a future refactor could quietly break.
 *
 * It also keeps chunk traffic away from the trace logging in [ChannelRegistry.sendMessage], which stringifies
 * every envelope it sends when trace is on - and `net.bestia.zone` runs at TRACE in development. Protobuf's
 * `toString` escapes each byte, so a three-kilobyte chunk becomes some fifteen kilobytes of log per send.
 */
interface ChunkFanOut {

  /**
   * @return how many accounts the bytes were actually written to; recipients with no live, writable channel
   *   are skipped rather than queued
   */
  fun fanOut(accountIds: Collection<Long>, message: SMSG): Int

  fun sendTo(accountId: Long, message: SMSG): Boolean = fanOut(listOf(accountId), message) == 1
}
