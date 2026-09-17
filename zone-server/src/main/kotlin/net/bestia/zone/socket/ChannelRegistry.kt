package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.Channel
import net.bestia.zone.message.SMSG
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@Profile("!no-socket")
class ChannelRegistry(
  config: SocketServerConfig
) : OutMessageHandler {

  private val logFilter = EnvelopeLogFilter(config.filterLogMessages)

  private val channelsByAccountId = ConcurrentHashMap<Long, Channel>()

  /**
   * Makes [channel] the account's one live connection and returns whichever channel it displaced,
   * or null if the account had none.
   *
   * The swap is a single atomic put on purpose: when two clients race to log in as the same account
   * they may be on different event loops, and this guarantees exactly one of them ends up registered
   * while the other is handed back to its displacer to be terminated. Checking-then-putting would let
   * both come away believing they own the account, leaving an orphaned socket that receives nothing
   * yet can still send commands.
   */
  fun registerChannel(accountId: Long, channel: Channel): Channel? {
    val displaced = channelsByAccountId.put(accountId, channel)
    LOG.debug { "Registered channel for account: $accountId" }

    return displaced
  }

  /**
   * Drops [channel]'s registration, but only while it is still the account's registered channel, and
   * reports whether this call was the one that removed it.
   *
   * The registration doubles as the ownership token for disconnect cleanup. A channel that a newer
   * login already displaced (see [registerChannel]) must not run the account's teardown a second
   * time — by then the teardown would hit the session belonging to the connection that replaced it.
   */
  fun unregisterChannel(accountId: Long, channel: Channel): Boolean {
    // Channel does not override equals, so this is an identity comparison - which is what we want.
    val removed = channelsByAccountId.remove(accountId, channel)

    if (removed) {
      LOG.debug { "Removed channel registration for account: $accountId" }
    } else {
      LOG.debug { "Channel for account $accountId was already replaced, leaving the registration alone" }
    }

    return removed
  }

  fun getChannel(accountId: Long): Channel? = channelsByAccountId[accountId]

  /**
   * A snapshot, deliberately: the keys of a [ConcurrentHashMap] are a live view, and a caller iterating one
   * while sending would be racing every login and logout in the process.
   */
  override val connectedAccountIds: Set<Long> get() = channelsByAccountId.keys.toSet()

  /** The same test [sendMessage] makes before giving up, without copying the whole key set to make it. */
  override fun isConnected(playerId: Long): Boolean = getChannel(playerId)?.isActive == true

  override fun sendMessage(playerId: Long, outMessage: SMSG) {
    val channel = getChannel(playerId)
    if (channel == null || !channel.isActive) {
      LOG.warn { "No active channel for player $playerId found" }
      return
    }

    write(playerId, channel, outMessage)
    channel.flush()
  }

  /**
   * One flush for the whole batch, which is the only reason this overload exists - see
   * [OutMessageHandler.sendMessages]. `write` queues onto the channel's event loop without touching
   * the socket, so the messages are framed in order and leave together.
   */
  override fun sendMessages(playerId: Long, outMessages: Collection<SMSG>) {
    if (outMessages.isEmpty()) return

    val channel = getChannel(playerId)
    if (channel == null || !channel.isActive) {
      LOG.warn { "No active channel for player $playerId found" }
      return
    }

    outMessages.forEach { write(playerId, channel, it) }
    channel.flush()
  }

  private fun write(playerId: Long, channel: Channel, outMessage: SMSG) {
    val envelope = outMessage.toBnetEnvelope()
    channel.write(envelope)

    if (LOG.isTraceEnabled() && logFilter.allows(envelope)) {
      LOG.trace { "TX player: $playerId - ${channel.remoteAddress()}: $envelope" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
