package net.bestia.zone.guild.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.guild.GuildDisbanding
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildInfoPublisher
import net.bestia.zone.guild.GuildService
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class DisbandGuildHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
  private val worldView: WorldView,
  private val connections: ConnectionInfoService,
  private val disbandings: List<GuildDisbanding>,
) : IoMessageHandler<DisbandGuildCMSG> {

  override val wire = decoder(MessageCase.DISBAND_GUILD) { accountId, envelope ->
    DisbandGuildCMSG.fromBnet(accountId, envelope.disbandGuild)
  }

  override fun handle(msg: DisbandGuildCMSG): Boolean {
    val refusal = refusalOf(msg.playerId)
    if (refusal != null) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(refusal))
      return true
    }

    try {
      val disbanded = guildService.disband(msg.playerId, msg.confirmName)

      publisher.disbanded(disbanded.guildId, disbanded.members)
      worldView.post { disbandings.forEach { it.disbanded(this, disbanded.guildId, disbanded.guildName) } }
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }

  /** Asked of the leader only, since nobody else may disband; the rest is [GuildService.disband]'s to refuse. */
  private fun refusalOf(accountId: Long): OpError? = worldView.read {
    val membership = get(connections.getSelectedMasterEntityId(accountId), GuildMembership::class)
    membership?.takeIf { it.isLeader }?.let { leader -> disbandings.firstNotNullOfOrNull { it.refusal(this, leader.guildId) } }
  }
}
