package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.Channel
import net.bestia.zone.message.SMSG
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@Profile("!no-socket")
class ChannelRegistry : OutMessageHandler {

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
            LOG.debug { "TX player: $playerId - ${channel.remoteAddress()}: $envelope" }
        } else {
            LOG.warn { "No active channel for player $playerId found" }
        }
    }

    companion object {
        private val LOG = KotlinLogging.logger { }
    }
}
