package net.bestia.zone.worldevent.raid

import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.core.World
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldRecreatedEvent
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import net.bestia.zone.worldevent.EventGround
import net.bestia.zone.worldevent.nearestRoad
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Which town draws raiders, and where they camp. A town or city with old enemies in its history can draw them. It
 * does when it is the richest such town of its province, its purse is not bled dry, and few masters stand in it.
 * The camp lies on the highest ground in sight of it, and the band robs travellers on the road on the way up.
 */
@Component
class RaidTowns(
  private val sites: SettlementSiteIndex,
  private val economy: SettlementEconomyService,
  private val provinces: ProvinceService,
  private val ground: EventGround,
  private val worldService: WorldService,
) {

  /** A town by its settlement index, where it stands in metres, and its province. */
  data class RaidTown(val index: Int, val centre: Vec2d, val provinceIndex: Int)

  /** Where the band camps, and the spot on the road where it robbed travellers. Voxels. */
  data class Camp(val at: Vec3L, val ambush: Vec3L)

  /** Every settlement in metres, and the towns with old enemies. Worked out once per world: history does not change. */
  private class Known(val positions: Map<Int, Vec2d>, val withGrudges: List<RaidTown>)

  @Volatile
  private var cached: Known? = null

  private val known: Known
    get() = cached ?: synchronized(this) { cached ?: find().also { cached = it } }

  @EventListener
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    cached = null
  }

  /** The town of the province raiders go for right now, or null. Tick thread: it reads the purses and the masters. */
  fun targetIn(world: World, provinceIndex: Int): RaidTown? =
    known.withGrudges
      .filter { it.provinceIndex == provinceIndex && mastersNear(world, it) < FEW_DEFENDERS }
      .mapNotNull { town -> worthRaiding(town)?.let { treasury -> town to treasury } }
      .maxByOrNull { (_, treasury) -> treasury }
      ?.first

  /** Where to stand in the town nearest [x], [y] metres, which for a camp is always the town it eyes. */
  fun townNearest(x: Double, y: Double): Vec3L? =
    known.positions.values.minByOrNull { hypot(it.x - x, it.y - y) }?.let { ground.grounded(it.x, it.y) }

  /** The highest ground [CAMP_METRES] out that is nearer this town than any other, or null when none is dry land. */
  fun campFor(town: RaidTown): Camp? {
    val voxel = worldService.config.voxelSize
    val camp = (0 until BEARINGS)
      .map { bearing -> 2 * PI * bearing / BEARINGS }
      .map { angle -> Vec2d(town.centre.x + cos(angle) * CAMP_METRES, town.centre.y + sin(angle) * CAMP_METRES) }
      .filter { isOutsideTowns(it, voxel) && nearestIndex(it) == town.index }
      .mapNotNull { ground.grounded(it.x, it.y) }
      .maxByOrNull { it.z }
      ?: return null

    val midway = Vec2d((town.centre.x + camp.x * voxel) / 2, (town.centre.y + camp.y * voxel) / 2)
    val road = nearestRoad(worldService.generated, midway, ROAD_REACH) ?: midway
    val ambush = ground.grounded(road.x, road.y) ?: return null

    return Camp(camp, ambush)
  }

  private fun find(): Known {
    val generated = worldService.generated
    val positions = SettlementLoreService.settlementPositions(generated)
    val withGrudges = positions
      .filter { (index, _) -> sites.siteOf(index)?.tier in RICH_TIERS }
      .filter { (index, _) -> generated.world.chronicle.eventsOf(Actor(ActorType.SETTLEMENT, index)).any { it.kind in GRUDGES } }
      .mapNotNull { (index, centre) ->
        provinces.provinceAt(centre.x, centre.y)?.let { RaidTown(index, centre, it.index) }
      }
    return Known(positions, withGrudges)
  }

  /** What the town holds, or null for a fallen town or one whose purse players emptied: nothing to take there. */
  private fun worthRaiding(town: RaidTown): Double? {
    val reference = economy.referenceOf(town.index) ?: return null
    val treasury = economy.marketOf(town.index)?.treasuryWith(0) ?: return null
    return treasury.takeIf { it >= reference.treasury }
  }

  private fun mastersNear(world: World, town: RaidTown): Int {
    val voxel = worldService.config.voxelSize
    var count = 0
    world.query(Master::class, Position::class).each { _ ->
      val at = get<Position>()
      if (hypot(at.x * voxel - town.centre.x, at.y * voxel - town.centre.y) <= DEFENDED_METRES) count++
    }
    return count
  }

  private fun isOutsideTowns(at: Vec2d, voxel: Double): Boolean =
    sites.siteCovering((at.x / voxel).toLong(), (at.y / voxel).toLong()) == null

  private fun nearestIndex(at: Vec2d): Int? =
    known.positions.minByOrNull { (_, centre) -> hypot(centre.x - at.x, centre.y - at.y) }?.key

  private companion object {
    /** A village has nothing worth a band's risk. */
    val RICH_TIERS = setOf(SettlementTier.CITY, SettlementTier.TOWN)

    /** What a town's history must hold for someone to bear it a grudge: war, plunder, a feud. */
    val GRUDGES = setOf(
      EventKind.WAR_DECLARED, EventKind.BATTLE, EventKind.SIEGE, EventKind.SETTLEMENT_SACKED, EventKind.CONQUEST,
      EventKind.CIV_REVOLT, EventKind.FEUD_BEGAN, EventKind.VENGEANCE_SWORN, EventKind.BETRAYAL,
      EventKind.ARTIFACT_PLUNDERED,
    )

    /** Three masters in town are enough to scare a band off. */
    const val FEW_DEFENDERS = 3
    const val DEFENDED_METRES = 1_000.0

    /** Close enough to watch the gate, far enough that no guard walks by. */
    const val CAMP_METRES = 1_500.0
    const val BEARINGS = 16

    const val ROAD_REACH = 600.0
  }
}
