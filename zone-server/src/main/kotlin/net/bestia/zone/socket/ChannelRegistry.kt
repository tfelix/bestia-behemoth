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

  private val logMessages = config.filterLogMessages.filter {
    !it.startsWith("!")
  }.toSet()

  private val notLogMessages = config.filterLogMessages.filter {
    it.startsWith("!")
  }.map { it.substring(1) }.toSet()

  private val channelsByAccountId = ConcurrentHashMap<Long, Channel>()

  fun registerChannel(accountId: Long, channel: Channel) {
    channelsByAccountId[accountId] = channel
    LOG.debug { "Registered channel for account: $accountId" }
  }

  fun unregisterChannel(accountId: Long) {
    channelsByAccountId.remove(accountId)
    LOG.debug { "Removed channel registration for account: $accountId" }
  }

  fun getChannel(accountId: Long): Channel? = channelsByAccountId[accountId]

  override fun sendMessage(playerId: Long, outMessage: SMSG) {
    val channel = getChannel(playerId)
    if (channel != null && channel.isActive) {
      val envelope = outMessage.toBnetEnvelope()
      channel.writeAndFlush(envelope)

      // Quite some complex log filtering if trace is enabled
      if (LOG.isTraceEnabled()) {
        val envelopeTxt = envelope.toString()
        val isLogMessage = logMessages.isEmpty() || logMessages.any { envelopeTxt.contains(it) }
        val isNotLogMessage = notLogMessages.isEmpty() || notLogMessages.none { envelopeTxt.contains(it) }
        if (isLogMessage && isNotLogMessage) {
          LOG.trace {
            "TX player: $playerId - ${channel.remoteAddress()}: $envelope"
          }
        }
      }
    } else {
      LOG.warn { "No active channel for player $playerId found" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
