package net.bestia.zone.guild.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildInfoPublisher
import net.bestia.zone.guild.GuildService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class SetGuildMemberRankHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<SetGuildMemberRankCMSG> {

  override val wire = decoder(MessageCase.SET_GUILD_MEMBER_RANK) { accountId, envelope ->
    SetGuildMemberRankCMSG.fromBnet(accountId, envelope.setGuildMemberRank)
  }

  override fun handle(msg: SetGuildMemberRankCMSG): Boolean {
    try {
      publisher.request(guildService.setRank(msg.playerId, msg.masterId, msg.rankId))
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
