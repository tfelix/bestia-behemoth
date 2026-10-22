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
class InvestGuildSkillHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<InvestGuildSkillCMSG> {

  override val wire = decoder(MessageCase.INVEST_GUILD_SKILL) { accountId, envelope ->
    InvestGuildSkillCMSG.fromBnet(accountId, envelope.investGuildSkill)
  }

  override fun handle(msg: InvestGuildSkillCMSG): Boolean {
    try {
      publisher.request(guildService.investSkill(msg.playerId, msg.skillId))
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
