package net.bestia.zone.cartography.chart

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.WorldWrap
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.cartography.coverage.Coverage
import net.bestia.zone.cartography.coverage.CoverageCodec
import net.bestia.zone.cartography.coverage.SurveyGrid
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Everything that makes, joins and reads charts.
 *
 * Runs off the tick thread - every method here touches the database, so a caller on `zone-tick` has to reach it
 * through `AsyncJobExecutor` (see [net.bestia.zone.cartography.SurveyService], which is the one place that
 * does).
 *
 * ### Only surveying needs the skill
 *
 * `CARTOGRAPHY` creates charts and nothing else. [merge] and [copy] are inventory operations gated on holding
 * the right items, so a player who has never taken the skill can still buy two charts and join them - which is
 * what makes charts worth trading rather than merely worth owning.
 *
 * ### No cache, deliberately
 *
 * [inventoryCoverage] reads rows every time it is asked, and the tile service will ask it often. The reason it
 * is not memoised here is that this class cannot see every event that would invalidate it: minting, merging and
 * copying all pass through here, but dropping a chart, looting one and eventually trading one do not. A cache
 * with three of its five invalidation points would serve a player ground they had sold. It belongs with the
 * tile service, which can watch inventory changes as a whole.
 */
@Service
class ChartService(
  private val worldService: WorldService,
  private val charts: MapChartRepository,
  private val inventoryService: InventoryService,
  private val itemRepository: ItemRepository,
  private val masterRepository: MasterRepository,
) {

  /**
   * Rebuilt per call rather than cached, so a regenerated world is never surveyed against the old lattice.
   * Two `ceil`s and an allocation; the alternative is a field that has to be invalidated on world load.
   */
  val grid: SurveyGrid get() = SurveyGrid(WorldWrap(worldService.config))

  /**
   * Turns a completed survey into a chart in the master's inventory, consuming a blank.
   *
   * The blank is *taken* here and nowhere else, which is what makes a cancelled or interrupted survey free.
   * It is also checked earlier, at cast start, by `SurveyService.checkBlank` - not instead of this one but
   * before it, so a surveyor with nothing to draw on is told at the button rather than five seconds later.
   * The check here is the one that decides: a sheet counted then can be dropped or traded away while the bar
   * fills, and only this one shares a transaction with the write. `CraftingService` checks its inputs at
   * `start` and takes them at `resolve` for the same reason.
   */
  @Transactional
  fun mint(masterId: Long, centreX: Double, centreY: Double, radiusMetres: Double): Result {
    val blank = itemRepository.findByIdentifier(BLANK_IDENTIFIER)
    val chartItem = itemRepository.findByIdentifier(CHART_IDENTIFIER)
    if (blank == null || chartItem == null) {
      // items.yml is imported at boot, so this is a broken deployment rather than anything a player did.
      LOG.error { "items.yml is missing '$BLANK_IDENTIFIER' or '$CHART_IDENTIFIER'; nobody can chart anything" }
      return Result.Refused(OpError.CHART_NOT_FOUND)
    }

    if (inventoryService.removeOneFromMaster(masterId, blank.id, 1) == null) {
      return Result.Refused(OpError.CHART_NEEDS_BLANK)
    }

    val coverage = Coverage(grid)
    coverage.fillDisc(centreX, centreY, radiusMetres)

    return Result.Ok(write(masterId, chartItem, coverage), chartItem, coverage.cellCount(), blank.id)
  }

  /**
   * Joins [fromUniqueId] into [intoUniqueId] and destroys the source chart.
   *
   * The union goes into the chart that stays, so the player keeps whichever one they aimed at - and the other
   * is consumed, because a merge that left both would make charts free to duplicate.
   */
  @Transactional
  fun merge(masterId: Long, intoUniqueId: Long, fromUniqueId: Long): Result {
    if (intoUniqueId == fromUniqueId) return Result.Refused(OpError.CHART_MERGE_SAME)

    val grid = grid
    val into = heldChart(masterId, intoUniqueId) ?: return Result.Refused(OpError.CHART_NOT_FOUND)
    val from = heldChart(masterId, fromUniqueId) ?: return Result.Refused(OpError.CHART_NOT_FOUND)

    val target = readable(into, grid) ?: return Result.Refused(OpError.CHART_STALE_WORLD)
    val source = readable(from, grid) ?: return Result.Refused(OpError.CHART_STALE_WORLD)

    target.orWith(source)
    into.coverage = CoverageCodec.encode(target)
    into.worldShapeVersion = worldService.record.shapeVersion
    charts.save(into)

    // The row first, then the instance: the chart row references the instance, so the other order would leave a
    // dangling reference for as long as the transaction lasted.
    charts.delete(from)
    inventoryService.destroyInstance(masterId, fromUniqueId)

    LOG.debug { "Master $masterId merged chart $fromUniqueId into $intoUniqueId, now ${target.cellCount()} cells" }
    return Result.Ok(intoUniqueId, into.itemInstance.item, target.cellCount())
  }

  /** Duplicates a chart onto a blank, which is what lets one be sold without giving up the knowledge. */
  @Transactional
  fun copy(masterId: Long, uniqueId: Long): Result {
    val grid = grid
    val source = heldChart(masterId, uniqueId) ?: return Result.Refused(OpError.CHART_NOT_FOUND)
    val coverage = readable(source, grid) ?: return Result.Refused(OpError.CHART_STALE_WORLD)

    val blank = itemRepository.findByIdentifier(BLANK_IDENTIFIER) ?: return Result.Refused(OpError.CHART_NOT_FOUND)
    if (inventoryService.removeOneFromMaster(masterId, blank.id, 1) == null) {
      return Result.Refused(OpError.CHART_NEEDS_BLANK)
    }

    val chartItem = source.itemInstance.item
    return Result.Ok(write(masterId, chartItem, coverage), chartItem, coverage.cellCount(), blank.id)
  }

  /**
   * Everything a master can currently see: the union of the charts in their inventory.
   *
   * Charts written for another lattice or another world's shape are skipped rather than refused, because one
   * unreadable chart among five must not blank the map. They stay on the row, so a chart that becomes readable
   * again - it will not, but the data is not thrown away - is not lost.
   */
  @Transactional(readOnly = true)
  fun inventoryCoverage(masterId: Long): Coverage {
    val grid = grid
    val union = Coverage(grid)

    val master = masterRepository.findByIdOrThrow(masterId)
    val held = master.container.slots.mapNotNull { it.itemInstance?.id }
    if (held.isEmpty()) return union

    for (chart in charts.findAllByItemInstanceIdIn(held)) {
      union.orWith(readable(chart, grid) ?: continue)
    }

    return union
  }

  /** The chart a new master is created holding. Blank-free: there is nothing to consume at creation. */
  @Transactional
  fun grantStarterChart(masterId: Long, centreX: Double, centreY: Double, radiusMetres: Double): Result {
    val chartItem = itemRepository.findByIdentifier(CHART_IDENTIFIER)
      ?: return Result.Refused(OpError.CHART_NOT_FOUND)

    val coverage = Coverage(grid)
    coverage.fillDisc(centreX, centreY, radiusMetres)

    return Result.Ok(write(masterId, chartItem, coverage), chartItem, coverage.cellCount())
  }

  /** Mints the instance, hangs a chart row off it, and answers the instance id. */
  private fun write(masterId: Long, chartItem: Item, coverage: Coverage): Long {
    val instance = inventoryService.mintInstanceForMaster(masterId, chartItem)
    charts.save(MapChart(instance, CoverageCodec.encode(coverage), worldService.record.shapeVersion))

    return instance.id
  }

  private fun heldChart(masterId: Long, uniqueId: Long): MapChart? {
    val instance = inventoryService.heldInstance(masterId, uniqueId) ?: return null
    return charts.findByItemInstanceId(instance.id)
  }

  /** The chart's coverage, or null when it describes a world or a lattice that no longer applies. */
  private fun readable(chart: MapChart, grid: SurveyGrid): Coverage? {
    if (chart.worldShapeVersion != worldService.record.shapeVersion) {
      LOG.debug { "Chart ${chart.id} was surveyed in shape ${chart.worldShapeVersion}, discarding it" }
      return null
    }
    if (!CoverageCodec.isReadableBy(chart.coverage, grid)) {
      LOG.debug { "Chart ${chart.id} was written for another survey lattice, discarding it" }
      return null
    }

    return CoverageCodec.decode(chart.coverage, grid)
  }

  /** What happened, in terms the caller can either report to a client or act on. */
  sealed interface Result {

    /**
     * @property uniqueId the chart instance the player now holds
     * @property item its template, which is what the live inventory mirror needs to list it
     * @property cells how much ground it covers, for logging and for the client's own display
     * @property consumedBlankItemId the blank this operation used up, or null when it used none. Carried
     *   rather than looked up again because the live [net.bestia.zone.ecs.item.Inventory] has to be corrected
     *   by whoever applies this, and it is the one fact about the operation that is not visible from the
     *   result otherwise.
     */
    data class Ok(
      val uniqueId: Long,
      val item: Item,
      val cells: Long,
      val consumedBlankItemId: Long? = null
    ) : Result

    data class Refused(val error: OpError) : Result
  }

  companion object {

    /** `items.yml` identifiers. Charting is the only thing that reads them, so they live here. */
    const val CHART_IDENTIFIER = "chart"
    const val BLANK_IDENTIFIER = "chart_blank"

    private val LOG = KotlinLogging.logger { }
  }
}
