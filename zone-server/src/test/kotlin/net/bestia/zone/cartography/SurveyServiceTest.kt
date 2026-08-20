package net.bestia.zone.cartography

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.ItemTemplateRegistry
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The gate a survey passes before its cast bar goes up.
 *
 * Only [SurveyService.checkBlank] is exercised here: it is the half that runs on the world lock, and the half
 * whose whole point is answering *before* the channel rather than after it. What the survey then costs is
 * taken relationally at the far end and belongs to `ChartServiceTest`.
 */
class SurveyServiceTest {

  private val world: World = testWorld()

  private val chartService = mockk<ChartService>(relaxed = true)
  private val worldService = mockk<WorldService>(relaxed = true)
  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)
  private val asyncJobExecutor = mockk<AsyncJobExecutor>(relaxed = true)

  private val itemTemplates = mockk<ItemTemplateRegistry>().also {
    every { it.idOf(ChartService.BLANK_IDENTIFIER) } returns BLANK_ITEM
  }

  private val sut = SurveyService(
    chartService = chartService,
    asyncJobExecutor = asyncJobExecutor,
    worldService = worldService,
    outMessageProcessor = outMessageProcessor,
    itemTemplates = itemTemplates
  )

  /** The blank is spent when the survey lands, so a cast that is cancelled halfway must cost nothing. */
  @Test
  fun `a surveyor carrying a blank may start, and still carries it`() {
    val surveyor = givenSurveyor(blanks = 1)

    assertNull(sut.checkBlank(world, surveyor))

    assertEquals(1, blanksHeld(surveyor))
  }

  @Test
  fun `a surveyor with nothing to draw on is refused`() {
    val surveyor = givenSurveyor(blanks = 0)

    assertEquals(OpError.CHART_NEEDS_BLANK, sut.checkBlank(world, surveyor))
  }

  /** Only a master holds charts, so a bestia is refused before it can channel for one. */
  @Test
  fun `an entity that is not a master cannot survey`() {
    val surveyor = givenSurveyor(blanks = 1)
    world.remove(surveyor, Master::class)

    assertEquals(OpError.CHART_NOT_FOUND, sut.checkBlank(world, surveyor))
  }

  @Test
  fun `a deployment whose items are missing refuses rather than casting for nothing`() {
    every { itemTemplates.idOf(ChartService.BLANK_IDENTIFIER) } returns null
    val surveyor = givenSurveyor(blanks = 1)

    assertEquals(OpError.CHART_NOT_FOUND, sut.checkBlank(world, surveyor))
  }

  private fun givenSurveyor(blanks: Int): EntityId = world.createEntity { id ->
    add(id, Master(masterId = MASTER_ID, name = "Surveyor"))
    add(
      id,
      Inventory(
        if (blanks > 0) {
          mutableListOf(Inventory.Item(itemId = BLANK_ITEM, amount = blanks, weight = 5))
        } else {
          mutableListOf()
        }
      )
    )
  }

  private fun blanksHeld(entityId: EntityId): Int =
    world.getOrThrow(entityId, Inventory::class).getItems().filter { it.itemId == BLANK_ITEM }.sumOf { it.amount }

  private companion object {
    const val MASTER_ID = 42L
    const val BLANK_ITEM = 22L
  }
}
