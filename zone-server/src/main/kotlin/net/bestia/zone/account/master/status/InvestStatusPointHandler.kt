package net.bestia.zone.account.master.status

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/**
 * Handles a client request to spend one or more status points across one or more base status
 * attributes in a single batch.
 */
@Component
class InvestStatusPointHandler(
  private val investStatusPointService: InvestStatusPointService,
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor
) : TickMessageHandler<InvestStatusPointCMSG> {
  override val wire = decoder(MessageCase.INVEST_STATUS_POINT) { accountId, envelope ->
    InvestStatusPointCMSG.fromBnet(accountId, envelope.investStatusPoint)
  }

  override fun handle(world: World, msg: InvestStatusPointCMSG): Boolean {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)
    val investments = msg.investedPoints.map { StatusPointInvestment(it.attribute, it.amount) }

    try {
      investStatusPointService.investStatusPoints(world, masterEntityId, investments)
    } catch (_: NoStatusPointsAvailableException) {
      // The status window prices every "+" against the master's base values before enabling it, so a
      // batch that overspends means a client bug rather than something the player did. Answered with
      // the generic code on purpose - there is no message a player would ever read here.
      LOG.warn { "Account ${msg.playerId} sent an unaffordable status point investment: $investments" }
      outMessageProcessor.sendToPlayer(
        msg.playerId,
        OperationErrorSMSG(OperationErrorProto.OpError.MASTER_GENERAL_ERROR)
      )
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
