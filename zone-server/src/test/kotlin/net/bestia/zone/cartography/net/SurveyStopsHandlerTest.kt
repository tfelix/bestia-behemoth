package net.bestia.zone.cartography.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.cartography.survey.TheodoliteSurveys
import net.bestia.zone.cartography.survey.TheodoliteSurveys.StopsOutcome
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import org.junit.jupiter.api.Test

/** The game client hears how its needle game went. */
class SurveyStopsHandlerTest {

  private val surveys = mockk<TheodoliteSurveys>()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val handler = SurveyStopsHandler(surveys, messages)
  private val stops = SurveyStopsCMSG(ACCOUNT_ID, structureId = 7, stopsMs = listOf(1, 2, 3))

  @Test
  fun `a won game says the survey started`() {
    every { surveys.submitStops(ACCOUNT_ID, 7, listOf(1, 2, 3)) } returns StopsOutcome.WON

    handler.handle(mockk(), stops)

    verify { messages.sendToPlayer(ACCOUNT_ID, OperationSuccessSMSG(OpSuccess.SURVEY_STARTED)) }
  }

  @Test
  fun `a missed game says so`() {
    every { surveys.submitStops(ACCOUNT_ID, 7, listOf(1, 2, 3)) } returns StopsOutcome.MISSED

    handler.handle(mockk(), stops)

    verify { messages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.SURVEY_MISSED)) }
  }

  @Test
  fun `stops without an open game are ignored`() {
    every { surveys.submitStops(ACCOUNT_ID, 7, listOf(1, 2, 3)) } returns null

    handler.handle(mockk(), stops)

    verify(exactly = 0) { messages.sendToPlayer(any(), any<SMSG>()) }
  }

  private companion object {
    const val ACCOUNT_ID = 1L
  }
}
