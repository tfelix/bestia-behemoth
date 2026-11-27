package net.bestia.zone.doomsday.destroyer

import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.prop.SettlementFateService
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.settlement.TownService
import net.bestia.zone.settlement.ecs.CitySign
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.StartTownMarkets
import net.bestia.zone.worldevent.EventGround
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * The towns the Destroyer marches on: the world's settlements and the players' towns still standing outside the
 * sanctuaries, so start towns are spared. Reads tick state, so callers hold the world.
 */
@Component
class DestroyerTowns(
  private val worldService: WorldService,
  private val fates: SettlementFateService,
  private val playerTowns: TownRegistry,
  private val townService: TownService,
  private val startTowns: StartTownMarkets,
  private val provinces: ProvinceService,
  private val eventGround: EventGround,
) {

  /** A town it can march on. */
  sealed interface Target {
    data class Settlement(val index: Int) : Target
    data class PlayerTown(val id: Long) : Target
  }

  private val settlements: Map<Int, Vec2d> by lazy { SettlementLoreService.settlementPositions(worldService.generated) }

  fun nearest(x: Double, y: Double, razed: List<Int>): Target? {
    return targets(razed).minByOrNull { metresTo(it, x, y) }
  }

  fun isTarget(target: Target, razed: List<Int>): Boolean {
    return when (target) {
      is Target.Settlement -> settlements[target.index]?.let { isTarget(target.index, it, razed) } == true
      is Target.PlayerTown -> playerTowns.of(target.id)?.let { it.isStanding && !isSanctuary(centreOf(it.sign)) } == true
    }
  }

  fun countLeft(razed: List<Int>): Int {
    return targets(razed).size
  }

  /** The ground at the town's centre, where the Destroyer heads. */
  fun groundAt(target: Target): Vec3L? {
    return when (target) {
      is Target.Settlement -> settlements[target.index]?.let { eventGround.grounded(it.x, it.y) }
      is Target.PlayerTown -> playerTowns.of(target.id)?.sign
    }
  }

  fun metresTo(target: Target, x: Double, y: Double): Double {
    val centre = when (target) {
      is Target.Settlement -> settlements[target.index]
      is Target.PlayerTown -> playerTowns.of(target.id)?.sign?.let(::centreOf)
    } ?: return Double.MAX_VALUE
    return hypot(centre.x - x, centre.y - y)
  }

  /** A settlement loses every building. A player town falls as if its sign were broken, seen or not. */
  fun raze(world: World, target: Target) {
    when (target) {
      is Target.Settlement -> fates.raze(world, target.index)
      is Target.PlayerTown -> breakSign(world, target.id)
    }
  }

  private fun breakSign(world: World, townId: Long) {
    var sign: Long? = null
    world.query(CitySign::class).each { id -> if (get<CitySign>().townId == townId) sign = id }
    val standing = sign
    if (standing != null) {
      world.add(standing, Dead())
    } else {
      townService.fall(townId)
    }
  }

  private fun targets(razed: List<Int>): List<Target> {
    val standingSettlements = settlements
      .filter { (index, centre) -> isTarget(index, centre, razed) }
      .map { (index, _) -> Target.Settlement(index) }
    val standingTowns = playerTowns.all()
      .filter { it.isStanding && !isSanctuary(centreOf(it.sign)) }
      .map { Target.PlayerTown(it.id) }
    return standingSettlements + standingTowns
  }

  private fun isTarget(index: Int, centre: Vec2d, razed: List<Int>): Boolean {
    return index !in razed &&
      !fates.hasFallen(index) &&
      !startTowns.isStartTown(index) &&
      !isSanctuary(centre)
  }

  private fun isSanctuary(centre: Vec2d): Boolean {
    return provinces.isSanctuaryAt(centre.x, centre.y)
  }

  private fun centreOf(sign: Vec3L): Vec2d {
    val metres = worldService.config.voxelSize
    return Vec2d(sign.x * metres, sign.y * metres)
  }
}
