package net.bestia.zone.guild.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildLog
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Any member may read the log; the guild window asks whenever its Log tab is shown. */
@Component
class RequestGuildLogHandler(
  private val guildLog: GuildLog,
  private val sessions: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<RequestGuildLogCMSG> {

  override val wire = decoder(MessageCase.REQUEST_GUILD_LOG) { accountId, envelope ->
    RequestGuildLogCMSG.fromBnet(accountId, envelope.requestGuildLog)
  }

  override fun handle(msg: RequestGuildLogCMSG): Boolean {
    val masterId = sessions.activeMasterIdOf(msg.playerId) ?: return true

    try {
      outMessageProcessor.sendToPlayer(msg.playerId, guildLog.newestFor(masterId))
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
