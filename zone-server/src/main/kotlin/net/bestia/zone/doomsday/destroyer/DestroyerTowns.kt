package net.bestia.zone.doomsday.destroyer

import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.prop.SettlementFateService
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.StartTownMarkets
import net.bestia.zone.worldevent.EventGround
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * The towns the Destroyer marches on: every settlement still standing outside the sanctuaries, so start towns are
 * spared. Reads the settlements' fates, which are tick state, so callers hold the world.
 */
@Component
class DestroyerTowns(
  private val worldService: WorldService,
  private val fates: SettlementFateService,
  private val startTowns: StartTownMarkets,
  private val provinces: ProvinceService,
  private val eventGround: EventGround,
) {

  private val centres: Map<Int, Vec2d> by lazy { SettlementLoreService.settlementPositions(worldService.generated) }

  /** The ground at the town's centre, where the Destroyer heads. */
  fun groundAt(town: Int): Vec3L? {
    val centre = centres[town] ?: return null
    return eventGround.grounded(centre.x, centre.y)
  }

  fun metresTo(town: Int, x: Double, y: Double): Double {
    val centre = centres[town] ?: return Double.MAX_VALUE
    return hypot(centre.x - x, centre.y - y)
  }

  fun nearest(x: Double, y: Double, razed: List<Int>): Int? {
    return centres
      .filter { (town, centre) -> isTarget(town, centre, razed) }
      .minByOrNull { (_, centre) -> hypot(centre.x - x, centre.y - y) }
      ?.key
  }

  fun isTarget(town: Int, razed: List<Int>): Boolean {
    val centre = centres[town] ?: return false
    return isTarget(town, centre, razed)
  }

  fun countLeft(razed: List<Int>): Int {
    return centres.count { (town, centre) -> isTarget(town, centre, razed) }
  }

  private fun isTarget(town: Int, centre: Vec2d, razed: List<Int>): Boolean {
    return town !in razed &&
      !fates.hasFallen(town) &&
      !startTowns.isStartTown(town) &&
      !provinces.isSanctuaryAt(centre.x, centre.y)
  }
}
