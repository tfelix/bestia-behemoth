package net.bestia.zone.account.master.status

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Handles a client request to spend one or more status points across one or more base status
 * attributes in a single batch.
 */
@Component
class InvestStatusPointHandler(
  private val investStatusPointService: InvestStatusPointService,
  private val masterResolver: MasterResolver
) : InMessageProcessor.IncomingMessageHandler<InvestStatusPointCMSG> {
  override val handles = InvestStatusPointCMSG::class

  override fun handle(msg: InvestStatusPointCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val master = masterResolver.getSelectedMasterByAccountId(msg.playerId)
    val investments = msg.investedPoints.map { StatusPointInvestment(it.attribute, it.amount) }
    investStatusPointService.investStatusPoints(master.id, investments)

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
