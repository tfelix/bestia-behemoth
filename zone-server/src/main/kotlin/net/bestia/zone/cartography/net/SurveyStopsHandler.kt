package net.bestia.zone.cartography.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.cartography.survey.TheodoliteSurveys
import net.bestia.zone.cartography.survey.TheodoliteSurveys.StopsOutcome
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/** The player's stops in a survey's needle game. On the tick, since a win starts the theodolite's work. */
@Component
class SurveyStopsHandler(
  private val surveys: TheodoliteSurveys,
  private val messages: OutMessageProcessor,
) : TickMessageHandler<SurveyStopsCMSG> {

  override val wire = decoder(MessageCase.SURVEY_STOPS) { accountId, envelope ->
    SurveyStopsCMSG.fromBnet(accountId, envelope.surveyStops)
  }

  override fun handle(world: World, msg: SurveyStopsCMSG): Boolean {
    val reply = when (surveys.submitStops(msg.playerId, msg.structureId, msg.stopsMs)) {
      StopsOutcome.WON -> OperationSuccessSMSG(OpSuccess.SURVEY_STARTED)
      StopsOutcome.MISSED -> OperationErrorSMSG(OpError.SURVEY_MISSED)
      null -> return true
    }
    messages.sendToPlayer(msg.playerId, reply)

    return true
  }
}
