package net.bestia.zone.cartography.survey

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.cartography.survey.TheodoliteSurveys.StopsOutcome
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TheodoliteSurveysTest {

  private val theodolite = StructureEntry(
    id = 7, kind = StaticEntityKind.THEODOLITE, ownerMasterId = MASTER_ID, ownerAccountId = ACCOUNT_ID,
    position = Vec3L(0, 0, 0), yaw = 0f
  )
  private val structures = mockk<PlayerStructureRegistry>(relaxed = true)
  private val sent = mutableListOf<SMSG>()
  private val messages = mockk<OutMessageProcessor> {
    every { sendToPlayer(ACCOUNT_ID, any<SMSG>()) } answers { sent += secondArg<SMSG>() }
  }
  private val sut = TheodoliteSurveys(
    structures = structures,
    chartService = mockk { every { inventoryCoverage(MASTER_ID) } returns mockk { every { contains(any(), any()) } returns false } },
    surveyService = mockk(relaxed = true),
    mana = mockk { every { manaAt(any()) } returns 0.5 },
    worldService = mockk { every { config.voxelSize } returns 1.0 },
    asyncJobExecutor = mockk { every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } },
    messages = messages,
  )

  @Test
  fun `a cast opens the needle game at the theodolite`() {
    sut.open(MASTER_ID, ACCOUNT_ID, theodolite, SurveyTier.KM_1, cartographyLevel = 1)

    val opened = sent.single() as SurveyMinigameSMSG
    assertEquals(theodolite.id, opened.structureId)
    assertEquals(SurveyMinigame.NEEDLES, opened.game.arcCentresDegrees.size)
  }

  @Test
  fun `a game prepared for the app is kept for its stops like a cast's`() {
    val game = sut.prepare(MASTER_ID, ACCOUNT_ID, theodolite, SurveyTier.KM_3, cartographyLevel = 3)

    assertEquals(StopsOutcome.WON, sut.submitStops(ACCOUNT_ID, theodolite.id, winningStops(game)))
    verify { structures.setSurvey(theodolite.id, SurveyTier.KM_3.radiusMetres, TheodoliteSurveys.WORK_SECONDS) }
    assertEquals(emptyList<SMSG>(), sent)
  }

  @Test
  fun `a won game starts ten minutes of work for the chosen size`() {
    val game = openedGame()

    val outcome = sut.submitStops(ACCOUNT_ID, theodolite.id, winningStops(game))

    assertEquals(StopsOutcome.WON, outcome)
    verify { structures.setSurvey(theodolite.id, SurveyTier.KM_1.radiusMetres, TheodoliteSurveys.WORK_SECONDS) }
  }

  @Test
  fun `a lost game starts nothing and cannot be retried without a new cast`() {
    val game = openedGame()
    val missed = winningStops(game).mapIndexed { i, stop -> if (i == 0) stop + game.swingPeriodMs / 2 else stop }

    assertEquals(StopsOutcome.MISSED, sut.submitStops(ACCOUNT_ID, theodolite.id, missed))
    assertNull(sut.submitStops(ACCOUNT_ID, theodolite.id, winningStops(game)))

    verify(exactly = 0) { structures.setSurvey(any(), any(), any()) }
  }

  @Test
  fun `only the player who cast can play the game`() {
    val game = openedGame()

    assertNull(sut.submitStops(ACCOUNT_ID + 1, theodolite.id, winningStops(game)))

    verify(exactly = 0) { structures.setSurvey(any(), any(), any()) }
  }

  private fun openedGame(): SurveyMinigame {
    sut.open(MASTER_ID, ACCOUNT_ID, theodolite, SurveyTier.KM_1, cartographyLevel = 1)
    return (sent.single() as SurveyMinigameSMSG).game
  }

  /** For each needle, the first millisecond of its round at which it points at its arc's centre. */
  private fun winningStops(game: SurveyMinigame): List<Long> {
    return game.arcCentresDegrees.map { centre ->
      (0L until game.swingPeriodMs).minBy { kotlin.math.abs(game.needleAt(it) - centre) }
    }
  }

  private companion object {
    const val MASTER_ID = 42L
    const val ACCOUNT_ID = 1L
  }
}
