package net.bestia.zone.guild.net

import io.github.oshai.kotlinlogging.KotlinLogging
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
      when (msg.skill) {
        InvestGuildSkillCMSG.GuildSkill.EXTENSION -> publisher.request(guildService.investExtension(msg.playerId))
        null -> {
          // The window offers Guild Extension and nothing else.
          LOG.warn { "Account ${msg.playerId} asked to invest in a guild skill this server does not know" }
          outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.REQUEST_REFUSED))
        }
      }
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
