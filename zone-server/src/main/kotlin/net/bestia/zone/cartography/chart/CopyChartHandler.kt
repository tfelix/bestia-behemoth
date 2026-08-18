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

/** Copies a held chart onto a blank. Ordered per master for the reason [MergeChartsHandler] is. */
@Component
class CopyChartHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val chartService: ChartService,
  private val world: WorldView,
  private val surveyService: SurveyService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor,
) : InMessageProcessor.IncomingMessageHandler<CopyChartCMSG> {
  override val handles = CopyChartCMSG::class

  override fun handle(msg: CopyChartCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val masterId = connectionInfoService.getMasterId(msg.playerId)
    val entityId = connectionInfoService.getActiveEntityId(msg.playerId)

    asyncJobExecutor.submit(masterId) {
      when (val result = chartService.copy(masterId, msg.uniqueId)) {
        is ChartService.Result.Refused ->
          outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(result.error))

        is ChartService.Result.Ok -> {
          surveyService.applyToLiveInventory(world, entityId, result)
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
