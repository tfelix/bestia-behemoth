package net.bestia.zone.account.master.status

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/**
 * Handles a client request to spend one or more status points across one or more base status
 * attributes in a single batch.
 */
@Component
class InvestStatusPointHandler(
  private val investStatusPointService: InvestStatusPointService,
  private val masterResolver: MasterResolver,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<InvestStatusPointCMSG> {
  override val handles = InvestStatusPointCMSG::class

  override fun handle(msg: InvestStatusPointCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val master = masterResolver.getSelectedMasterByAccountId(msg.playerId)
    val investments = msg.investedPoints.map { StatusPointInvestment(it.attribute, it.amount) }

    try {
      investStatusPointService.investStatusPoints(master.id, investments)
    } catch (_: NoStatusPointsAvailableException) {
      // The status window prices every "+" against the master's base values before enabling it, so a
      // batch that overspends means a client bug rather than something the player did. Answered with
      // the generic code on purpose - there is no message a player would ever read here.
      LOG.warn { "Master ${master.id} sent an unaffordable status point investment: $investments" }
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
