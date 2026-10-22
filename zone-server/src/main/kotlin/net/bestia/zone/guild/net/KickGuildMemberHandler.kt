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
class KickGuildMemberHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<KickGuildMemberCMSG> {

  override val wire = decoder(MessageCase.KICK_GUILD_MEMBER) { accountId, envelope ->
    KickGuildMemberCMSG.fromBnet(accountId, envelope.kickGuildMember)
  }

  override fun handle(msg: KickGuildMemberCMSG): Boolean {
    try {
      val departed = guildService.kick(msg.playerId, msg.masterId, msg.reason)

      publisher.left(departed.guildId, departed.member, GuildLeftSMSG.Reason.KICKED)
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
