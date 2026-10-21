package net.bestia.zone.guild.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildInfoPublisher
import net.bestia.zone.guild.GuildLeftSMSG
import net.bestia.zone.guild.GuildService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class LeaveGuildHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<LeaveGuildCMSG> {

  override val wire = decoder(MessageCase.LEAVE_GUILD) { accountId, envelope ->
    LeaveGuildCMSG.fromBnet(accountId, envelope.leaveGuild)
  }

  override fun handle(msg: LeaveGuildCMSG): Boolean {
    try {
      val departed = guildService.leave(msg.playerId)

      publisher.left(departed.guildId, departed.member, GuildLeftSMSG.Reason.LEFT)
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
