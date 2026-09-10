package net.bestia.zone.economy

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.world.PersistedWorld
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * When a settlement earns a database row, and when it loses one again.
 *
 * The first test is I18 and is the one the whole tier rests on: a world of three hundred towns is only
 * affordable because the ones nobody has done anything to are not written down. It is checked through
 * the *read* path on purpose, because that is the path where a row would be created by accident.
 */
class SettlementEconomyServiceTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val sites = mockk<SettlementSiteIndex>()
  private val repository = mockk<SettlementLedgerRepository>(relaxed = true)
  private val worldService = mockk<WorldService>()
  private val clock = mockk<BestiaClock>()

  private var now = BestiaDateTime(year = 1, month = 2, day = 3, hour = 12, minute = 0, second = 0)

  private val service = SettlementEconomyService(
    catalogue = catalogue,
    step = EconomyStep(catalogue, UndamagedCapacity(), UnclaimedProduction()),
    sites = sites,
    repository = repository,
    // Runs the durable write on the calling thread, so a test can assert on it without waiting.
    asyncJobExecutor = mockk<AsyncJobExecutor>().also {
      every { it.submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    },
    worldService = worldService,
    clock = clock,
  )

  init {
    every { sites.siteOf(VILLAGE) } returns site(population = 300)
    every { sites.siteOf(neq(VILLAGE)) } returns null
    every { worldService.record } returns mockk<PersistedWorld>().also {
      every { it.shapeVersion } returns SHAPE
      every { it.pipelineVersion } returns PIPELINE
    }
    every { clock.now() } answers { now }
    every { repository.findAll() } returns emptyList()
    // `save` is generic, and a relaxed mock answers a bare Object that cannot be cast back.
    every { repository.save(any()) } answers { firstArg() }
  }

  @Test
  fun `I18 - reading a town's prices never writes a row`() {
    val market = assertNotNull(service.marketOf(VILLAGE))

    assertTrue(market.priceOf("bread") > 0.0, "the read has to actually do something to prove anything")
    assertEquals(0, service.trackedSettlements, "an untouched settlement is being tracked")
    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `and still does not, however long the town has stood there`() {
    service.marketOf(VILLAGE)
    now = now.copy(year = 3)

    service.marketOf(VILLAGE)

    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `a settlement with no economy answers nothing rather than a free market`() {
    assertEquals(null, service.marketOf(VILLAGE + 1))
    assertEquals(null, service.referenceOf(VILLAGE + 1))
  }

  @Test
  fun `a disturbed town keeps a row, and drops it once it has come back`() {
    val reference = assertNotNull(service.referenceOf(VILLAGE))
    service.loadAll()

    // What a burnt-out village looks like on disk. Loading it is the only way in from here; a trade is
    // branch 12's job, and the point of this test is the *forgetting*.
    every { repository.findAll() } returns listOf(
      SettlementLedger.of(
        settlement = VILLAGE,
        state = LedgerState(
          deltaStock = reference.stock.mapValues { (_, standing) -> -standing },
          treasury = 0.0,
          lastStepDay = now.absoluteDay,
        ),
        shapeVersion = SHAPE,
        pipelineVersion = PIPELINE,
      )
    )
    service.loadAll()
    assertEquals(1, service.trackedSettlements, "the stored ledger was not picked up")

    // A month at a time, six times. Longer than I17's sixty days, and a month is exactly the step
    // ceiling - jumping six months in one go would be clamped to thirty days and simulate five of them
    // away, which is I15 working rather than the town failing to recover.
    repeat(6) {
      advanceOneMonth()
      service.catchUpAll()
    }

    assertEquals(0, service.trackedSettlements, "the village recovered and its row was never deleted")
    verify { repository.deleteById(VILLAGE) }
  }

  @Test
  fun `a row from another world is discarded rather than applied to whichever town has that index`() {
    val orphaned = SettlementLedger.of(VILLAGE, LedgerState(), shapeVersion = SHAPE + 1, pipelineVersion = PIPELINE)
    every { repository.findAll() } returns listOf(orphaned)

    val discarded = slot<List<SettlementLedger>>()
    every { repository.deleteAll(capture(discarded)) } returns Unit

    service.loadAll()

    assertEquals(0, service.trackedSettlements)
    assertEquals(listOf(orphaned), discarded.captured)
  }

  @Test
  fun `the books survive a round trip through the row`() {
    val state = LedgerState(
      deltaStock = mapOf("grain" to -42.5, "bread" to 3.25),
      deltaLogPrice = mapOf("grain" to 0.125),
      treasury = 1_234.5,
      lastStepDay = 77.0,
    )

    val restored = SettlementLedger.of(VILLAGE, state, SHAPE, PIPELINE).toState()

    assertEquals(state, restored)
    assertFalse(restored.deltaLogPrice.containsKey("bread"), "a commodity nothing was stored for came back")
  }

  private fun advanceOneMonth() {
    now = if (now.month == BestiaDateTime.MONTHS_PER_YEAR) {
      now.copy(year = now.year + 1, month = 1)
    } else {
      now.copy(month = now.month + 1)
    }
  }

  private fun site(population: Int) = SettlementSite(
    index = VILLAGE,
    centre = Vec2d(0.0, 0.0),
    tier = SettlementTier.entries.first(),
    population = PopulationSummary(
      settlement = VILLAGE,
      position = Vec2d(0.0, 0.0),
      population = population,
      wealth = 0.4,
      householdCount = population / 4,
      seed = 1L,
      businesses = emptyList(),
      sectors = IntArray(7),
      traffic = 1.5,
    ),
    unordered = emptyList(),
  )

  private companion object {
    const val VILLAGE = 12
    const val SHAPE = 900L
    const val PIPELINE = 700L
  }
}
