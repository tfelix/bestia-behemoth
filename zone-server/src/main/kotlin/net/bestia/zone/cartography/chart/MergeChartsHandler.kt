package net.bestia.zone.cartography.chart

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.cartography.SurveyService
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/**
 * Joins two held charts.
 *
 * Validation lives entirely in [ChartService.merge]; this resolves who is asking and reports the outcome.
 *
 * Done on an async worker rather than inline, keyed on the master. A handler runs on a network thread, so doing
 * the transaction here would hold that thread for the length of a database round trip - and more importantly the
 * key is what keeps two merges of the same three charts from interleaving, which is the one way a chart could be
 * consumed twice.
 */
@Component
class MergeChartsHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val chartService: ChartService,
  private val world: WorldView,
  private val surveyService: SurveyService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor,
) : InMessageProcessor.IncomingMessageHandler<MergeChartsCMSG> {
  override val handles = MergeChartsCMSG::class

  override fun handle(msg: MergeChartsCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val masterId = connectionInfoService.getMasterId(msg.playerId)
    val entityId = connectionInfoService.getActiveEntityId(msg.playerId)

    asyncJobExecutor.submit(masterId) {
      when (val result = chartService.merge(masterId, msg.intoUniqueId, msg.fromUniqueId)) {
        is ChartService.Result.Refused ->
          outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(result.error))

        is ChartService.Result.Ok -> {
          // Both go, then the survivor is listed again. Re-listing it is not busywork: its coverage changed,
          // and the client is holding the entry it was told about before the merge.
          surveyService.applyToLiveInventory(
            world, entityId, result, removedUniqueIds = listOf(msg.intoUniqueId, msg.fromUniqueId)
          )
          outMessageProcessor.sendToPlayer(msg.playerId, OperationSuccessSMSG(OpSuccess.CHART_WRITTEN))
        }
      }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
