package net.bestia.zone.cartography.chart

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterDeletionService
import net.bestia.zone.account.master.MasterFactory
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.cartography.coverage.CoverageCodec
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.world.MasterSpawnPointService
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a chart is worth: what surveying costs, what merging joins, and what a copy leaves behind.
 *
 * Against the real database rather than a mock container, because most of what could go wrong here is
 * relational - a chart row keyed by an item instance, a blank consumed inside the same transaction that mints
 * the chart, and a merge that has to delete a row before the row it points at.
 *
 * Every test creates its own master. The class is not `@Transactional` (see [MasterFactory.create], which runs
 * `REQUIRES_NEW`), so a shared one would carry the previous test's charts.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class ChartServiceTest {

  @Autowired
  private lateinit var chartService: ChartService

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var mapChartRepository: MapChartRepository

  @Autowired
  private lateinit var worldService: WorldService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var masterDeletionService: MasterDeletionService

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val created = mutableListOf<Master>()

  /**
   * Deletes the masters this class made.
   *
   * Not tidiness - an account has a hard cap on how many masters it may hold, so without this every test after
   * the first fails with [net.bestia.zone.account.master.MaxMastersReachedException]. It also puts the chart
   * foreign key through its cleanup path on every single test.
   */
  @AfterEach
  fun deleteCreatedMasters() {
    created.forEach { masterDeletionService.delete(ACCOUNT_ID, it.id, it.name) }
    created.clear()
  }

  @Test
  fun `a new master already holds a chart of the ground it woke up on`() {
    // Charts are the only source of map knowledge, so this is what stops a fresh master's map being blank.
    val master = givenMaster()
    val home = homePosition(master)

    val coverage = chartService.inventoryCoverage(master.id)

    assertFalse(coverage.isEmpty, "a new master has nothing charted at all")
    assertTrue(coverage.contains(home.first, home.second), "the starter chart does not cover the spawn point")
  }

  @Test
  fun `surveying with nothing to draw on is refused`() {
    val master = givenMaster()

    val result = chartService.mint(master.id, 40_000.0, 40_000.0, 1_000.0)

    assertEquals(OpError.CHART_NEEDS_BLANK, assertIs<ChartService.Result.Refused>(result).error)
  }

  @Test
  fun `surveying spends a blank and produces a chart of the surveyed ground`() {
    val master = givenMaster()
    inventoryService.addItem(master, ChartService.BLANK_IDENTIFIER, 2)

    val result = assertIs<ChartService.Result.Ok>(chartService.mint(master.id, 40_000.0, 40_000.0, 1_000.0))

    assertNotNull(mapChartRepository.findByItemInstanceId(result.uniqueId), "no chart row for the new instance")
    assertTrue(result.cells > 700, "a 1 km survey should be about 767 cells, was ${result.cells}")

    val coverage = chartService.inventoryCoverage(master.id)
    assertTrue(coverage.contains(40_000.0, 40_000.0))

    // One of the two blanks, and only one.
    assertEquals(1, blanksHeld(master.id))
  }

  @Test
  fun `merging joins both surveys into the chart that stays and consumes the other`() {
    val master = givenMaster()
    inventoryService.addItem(master, ChartService.BLANK_IDENTIFIER, 2)

    val west = assertIs<ChartService.Result.Ok>(chartService.mint(master.id, 30_000.0, 30_000.0, 1_000.0))
    val east = assertIs<ChartService.Result.Ok>(chartService.mint(master.id, 60_000.0, 60_000.0, 1_000.0))

    val merged = assertIs<ChartService.Result.Ok>(chartService.merge(master.id, west.uniqueId, east.uniqueId))

    assertEquals(west.uniqueId, merged.uniqueId, "the merge should land in the chart that was kept")
    assertEquals(west.cells + east.cells, merged.cells, "the two discs do not overlap, so the counts add")

    val kept = assertNotNull(mapChartRepository.findByItemInstanceId(west.uniqueId))
    val coverage = CoverageCodec.decode(kept.coverage, chartService.grid)
    assertTrue(coverage.contains(30_000.0, 30_000.0))
    assertTrue(coverage.contains(60_000.0, 60_000.0))

    // The consumed chart is gone from the inventory *and* from the chart table.
    assertNull(inventoryService.heldInstance(master.id, east.uniqueId))
    assertNull(mapChartRepository.findByItemInstanceId(east.uniqueId))
  }

  @Test
  fun `a chart cannot be merged into itself`() {
    val master = givenMaster()
    inventoryService.addItem(master, ChartService.BLANK_IDENTIFIER, 1)
    val chart = assertIs<ChartService.Result.Ok>(chartService.mint(master.id, 30_000.0, 30_000.0, 1_000.0))

    val result = chartService.merge(master.id, chart.uniqueId, chart.uniqueId)

    assertEquals(OpError.CHART_MERGE_SAME, assertIs<ChartService.Result.Refused>(result).error)
    assertNotNull(mapChartRepository.findByItemInstanceId(chart.uniqueId), "the refusal ate the chart")
  }

  @Test
  fun `merging something that is not a held chart is refused`() {
    val master = givenMaster()
    inventoryService.addItem(master, ChartService.BLANK_IDENTIFIER, 1)
    val chart = assertIs<ChartService.Result.Ok>(chartService.mint(master.id, 30_000.0, 30_000.0, 1_000.0))

    val result = chartService.merge(master.id, chart.uniqueId, fromUniqueId = 999_999L)

    assertEquals(OpError.CHART_NOT_FOUND, assertIs<ChartService.Result.Refused>(result).error)
  }

  @Test
  fun `copying spends a blank, leaves the original and produces the same coverage`() {
    val master = givenMaster()
    inventoryService.addItem(master, ChartService.BLANK_IDENTIFIER, 2)
    val original = assertIs<ChartService.Result.Ok>(chartService.mint(master.id, 30_000.0, 30_000.0, 1_000.0))

    val copy = assertIs<ChartService.Result.Ok>(chartService.copy(master.id, original.uniqueId))

    assertFalse(copy.uniqueId == original.uniqueId, "a copy has to be its own item")
    assertEquals(original.cells, copy.cells)
    assertNotNull(mapChartRepository.findByItemInstanceId(original.uniqueId), "copying consumed the original")
    assertEquals(0, blanksHeld(master.id), "both blanks should be gone: one surveyed, one copied onto")
  }

  @Test
  fun `copying with no blank is refused and leaves the original alone`() {
    val master = givenMaster()
    inventoryService.addItem(master, ChartService.BLANK_IDENTIFIER, 1)
    val original = assertIs<ChartService.Result.Ok>(chartService.mint(master.id, 30_000.0, 30_000.0, 1_000.0))

    val result = chartService.copy(master.id, original.uniqueId)

    assertEquals(OpError.CHART_NEEDS_BLANK, assertIs<ChartService.Result.Refused>(result).error)
    assertNotNull(mapChartRepository.findByItemInstanceId(original.uniqueId))
  }

  @Test
  fun `coverage is the union of every chart held`() {
    val master = givenMaster()
    inventoryService.addItem(master, ChartService.BLANK_IDENTIFIER, 2)
    chartService.mint(master.id, 30_000.0, 30_000.0, 1_000.0)
    chartService.mint(master.id, 60_000.0, 60_000.0, 1_000.0)

    val coverage = chartService.inventoryCoverage(master.id)

    assertTrue(coverage.contains(30_000.0, 30_000.0))
    assertTrue(coverage.contains(60_000.0, 60_000.0))
    assertFalse(coverage.contains(45_000.0, 45_000.0), "the gap between two charts must stay under fog")
  }

  @Test
  fun `a chart surveyed in a world that has been regenerated is ignored, not shown`() {
    // The coordinates are the same numbers over different terrain, so showing it would put its owner somewhere
    // they have never been. The row is kept - only reading it is refused.
    val master = givenMaster()
    inventoryService.addItem(master, ChartService.BLANK_IDENTIFIER, 1)
    val chart = assertIs<ChartService.Result.Ok>(chartService.mint(master.id, 30_000.0, 30_000.0, 1_000.0))

    val row = assertNotNull(mapChartRepository.findByItemInstanceId(chart.uniqueId))
    row.worldShapeVersion = worldService.record.shapeVersion + 1
    mapChartRepository.save(row)

    assertFalse(chartService.inventoryCoverage(master.id).contains(30_000.0, 30_000.0))
    assertNotNull(mapChartRepository.findByItemInstanceId(chart.uniqueId), "the stale chart was thrown away")
  }

  /** Blanks in the master's bag, read off the container so a spent stack simply reports zero. */
  private fun blanksHeld(masterId: Long): Int {
    val blankId = requireNotNull(itemRepository.findByIdentifier(ChartService.BLANK_IDENTIFIER)) {
      "items.yml has no '${ChartService.BLANK_IDENTIFIER}'"
    }.id

    // Inside a transaction: the container's slots are a lazy collection, and this test class is deliberately
    // not @Transactional (see the class note), so reading them bare throws LazyInitializationException.
    return TransactionTemplate(transactionManager).execute {
      masterRepository.findByIdOrThrow(masterId).container.slots
        .filter { it.template.id == blankId }
        .sumOf { it.amount }
    }!!
  }

  private fun homePosition(master: Master): Pair<Double, Double> {
    val voxelSize = worldService.config.voxelSize
    return master.spawnPosition.x * voxelSize to master.spawnPosition.y * voxelSize
  }

  private fun givenMaster(): Master = createMaster().also { created += it }

  private fun createMaster(): Master = masterFactory.create(
    ACCOUNT_ID,
    MasterFactory.CreateMasterData(
      name = "surveyor${NEXT_NAME.getAndIncrement()}",
      hairColor = Color.BLUE,
      skinColor = Color.BLUE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1,
      spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt()
    )
  )

  private companion object {
    /** Account 3 of the fixture: it starts with a single master, so there are free slots to create into. */
    const val ACCOUNT_ID = 3L
    val NEXT_NAME = AtomicInteger(1)
  }
}
