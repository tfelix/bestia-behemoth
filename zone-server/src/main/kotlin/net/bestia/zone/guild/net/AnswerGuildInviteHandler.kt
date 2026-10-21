package net.bestia.zone.guild.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildInfoPublisher
import net.bestia.zone.guild.GuildService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/** Yes joins and gives every member the new guild; no tells the inviter who said it, as a declined trade does. */
@Component
class AnswerGuildInviteHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<AnswerGuildInviteCMSG> {

  override val wire = decoder(MessageCase.ANSWER_GUILD_INVITE) { accountId, envelope ->
    AnswerGuildInviteCMSG.fromBnet(accountId, envelope.answerGuildInvite)
  }

  override fun handle(msg: AnswerGuildInviteCMSG): Boolean {
    try {
      if (msg.accept) {
        publisher.request(guildService.accept(msg.playerId, msg.invitationId))
      } else {
        val declined = guildService.decline(msg.playerId, msg.invitationId)
        outMessageProcessor.sendToPlayer(
          declined.inviterAccountId,
          OperationErrorSMSG(OpError.GUILD_INVITE_DECLINED, listOf(declined.invitedMasterName))
        )
      }
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
