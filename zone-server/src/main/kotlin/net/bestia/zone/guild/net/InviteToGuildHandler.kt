package net.bestia.zone.guild.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class InviteToGuildHandler(
  private val guildService: GuildService,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<InviteToGuildCMSG> {

  override val wire = decoder(MessageCase.INVITE_TO_GUILD) { accountId, envelope ->
    InviteToGuildCMSG.fromBnet(accountId, envelope.inviteToGuild)
  }

  override fun handle(msg: InviteToGuildCMSG): Boolean {
    try {
      val sent = guildService.invite(msg.playerId, msg.masterName)

      outMessageProcessor.sendToPlayer(sent.invitedAccountId, sent.invitation)
      outMessageProcessor.sendToPlayer(msg.playerId, OperationSuccessSMSG(OpSuccess.GUILD_INVITATION_SENT))
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
