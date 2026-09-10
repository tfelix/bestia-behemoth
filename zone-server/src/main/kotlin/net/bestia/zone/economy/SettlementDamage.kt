package net.bestia.zone.economy

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.pop.Catchment
import net.bestia.zone.world.WorldGenConfig
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.fire.ScorchRegistry
import net.bestia.zone.world.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * What a player's destruction has taken off a settlement's ability to work.
 *
 * ### Iterate the damage, not the world
 *
 * Counting what is standing is not merely inaccurate, it is backwards: static props exist only while a
 * client holds the column they stand on, so on an empty server every settlement would read zero. And a
 * 128 km world holds on the order of a million tree props, so enumerating a catchment is not affordable
 * either. The damage, though, is tiny - a handful of scarred columns in ordinary play and zero most of
 * the time - so it is the damage that gets walked.
 *
 * Buildings are the exception and go the other way: a settlement has tens of them and their prop ids
 * are known, so asking the divergence map about each is cheaper than walking a map that may hold
 * thousands of felled trees.
 *
 * ### Two channels, not three
 *
 * Burnt ground and destroyed workplaces. The design's third - felled and collected props - has no
 * reader yet, because no trade in the catalogue takes a prop as an input: the one that would, the
 * carpenter, is blocked on there being no `log` item. Building a channel nothing consumes is how this
 * codebase ends up with subsystems that are complete, tested and never reached.
 *
 * ### Read-only, and it must stay that way
 *
 * A regrown prop lingers in the divergence map until somebody walks past its column, because eviction
 * only runs on materialised ones. So this applies the regrowth deadline itself and **never evicts** -
 * that map's contract names exactly two writers, and the economy becoming a third is a race nobody
 * would find until a tree came back twice.
 */
@Service
class SettlementDamage(
  private val sites: SettlementSiteIndex,
  private val scorch: ScorchRegistry,
  private val divergence: WorldObjectDivergenceRegistry,
  private val catalogue: EconomyCatalogue,
  private val worldService: WorldService,
  private val settings: WorldGenConfig,
) : SettlementCapacity {

  /** What one settlement's trades can manage, and when it was worked out. */
  private class Assessment(val atNanos: Long, val byTrade: Map<String, Double>)

  private val assessed = HashMap<Int, Assessment>()

  override fun capacityOf(settlement: Int, trade: String): Double {
    return assessmentOf(settlement).byTrade[trade] ?: 1.0
  }

  /**
   * Every settlement with a scar inside its catchment.
   *
   * The reverse of the usual question, and the only place it is asked that way. Scars are few - a
   * handful in ordinary play and none most of the time - so walking them and asking which towns are
   * near is affordable, where walking the towns and asking which are scarred would not be.
   *
   * Destroyed buildings are deliberately not here. Knocking one down means standing in the town, so
   * the ordinary read path has already brought that settlement into the books; a fire can be set and
   * walked away from, which is the case that needs pushing.
   */
  override fun damagedSettlements(): Set<Int> {
    val keys = scorch.scarredKeys()
    if (keys.isEmpty()) return emptySet()

    val config = worldService.config
    val chunkMetres = config.chunkSize * config.voxelSize
    val reach = (LARGEST_CATCHMENT_METRES / config.voxelSize).toLong()

    val near = LinkedHashSet<Int>()
    for (key in keys) {
      val x = ((key shr 32).toInt() + 0.5) * chunkMetres / config.voxelSize
      val y = (key.toInt() + 0.5) * chunkMetres / config.voxelSize
      near.addAll(sites.coveringWithin(x.toLong(), y.toLong(), reach))
    }

    // The coarse pass over-selects on purpose - one reach for every tier - so the assessment itself is
    // what decides whether the fire actually fell on that town's fields.
    return near.filterTo(LinkedHashSet()) { settlement ->
      assessmentOf(settlement).byTrade.values.any { it < 1.0 }
    }
  }

  /** Forgets what it worked out, for a test and for a GM tool that has just changed the world. */
  fun invalidate() {
    assessed.clear()
  }

  private fun assessmentOf(settlement: Int): Assessment {
    val now = System.nanoTime()
    assessed[settlement]?.let { if (now - it.atNanos < RECHECK_NANOS) return it }

    val fresh = Assessment(now, assess(settlement))
    assessed[settlement] = fresh

    return fresh
  }

  private fun assess(settlement: Int): Map<String, Double> {
    val site = sites.siteOf(settlement) ?: return emptyMap()
    val ground = groundHealthOf(site)

    return catalogue.trades().associate { trade ->
      val workplaces = workplaceHealthOf(site, trade)
      // Ground damage reaches the trades that work the land and nothing else - a burnt field does not
      // stop a mill turning, it stops there being anything to put in it, and the Leontief minimum in
      // the step is what carries that downstream.
      val health = if (trade.building == BuildingFunction.FARM) workplaces * ground else workplaces

      trade.id to health
    }
  }

  /**
   * I20's workplace ceiling: losing every building of a trade leaves it at a fifth, not at nothing.
   *
   * Per channel rather than a floor on overall health, because it is explicable to a player - a perfect
   * siege leaves a town wretched, not dead - and because with buildings now permanent this ceiling is
   * the only thing standing between a determined player and a town that never recovers.
   *
   * Which building held which trade is exact rather than guessed: a business marker sits on its host
   * building's centre and both quantise to the same lattice, so `SettlementSite` already carries the
   * join. A trade with no building of its own falls back to the function it works from.
   */
  private fun workplaceHealthOf(site: SettlementSite, trade: Trade): Double {
    val ofTrade = trade.business?.let { business ->
      val type = catalogue.businessTypeOf(business)
      site.buildings.filter { it.businessType == type }
    }.orEmpty()

    val buildings = ofTrade.ifEmpty { site.buildingsOf(trade.building) }
    if (buildings.isEmpty()) return 1.0

    val standing = buildings.count { !isDestroyed(it.propId) }
    val share = standing.toDouble() / buildings.size

    return max(1.0 - WORKPLACE_CEILING, share)
  }

  /**
   * How much of the land a settlement actually works is burnt, as I20's scorch ceiling allows.
   *
   * The area under the plough is not the catchment: a village works as much of its potential as it
   * eats, so the worked share is `population / foodCapacity` of the disc. That falls out with no
   * constant of its own and says the right thing - a town on rich ground farms a smaller part of it.
   */
  private fun groundHealthOf(site: SettlementSite): Double {
    val farmed = farmedSquareMetresOf(site)
    if (farmed <= 0.0) return 1.0

    val burnt = burntSquareMetresIn(site)
    if (burnt <= 0.0) return 1.0

    LOG.debug { "Settlement ${site.index}: ${burnt.toLong()} m² burnt of ${farmed.toLong()} m² worked" }

    return 1.0 - min(SCORCH_CEILING, burnt / farmed)
  }

  private fun farmedSquareMetresOf(site: SettlementSite): Double {
    val summary = site.population ?: return 0.0
    val radius = Catchment.radiusOf(site.tier, economyParams())
    val catchment = Math.PI * radius * radius * Catchment.MEAN_CLAIM_WEIGHT

    val worked = summary.population / max(1.0, summary.foodCapacity)

    return catchment * min(1.0, worked) * summary.cerealShare
  }

  /**
   * Burnt ground inside the catchment, each column weighted by the settlement's claim on it.
   *
   * The weight is the generator's own, reproduced rather than approximated: catchments overlap, and a
   * contested field whose two shares did not add up would hurt one village for free.
   */
  private fun burntSquareMetresIn(site: SettlementSite): Double {
    val keys = scorch.scarredKeys()
    if (keys.isEmpty()) return 0.0

    val config = worldService.config
    val radius = Catchment.radiusOf(site.tier, economyParams())
    val cellArea = config.voxelSize * config.voxelSize
    val chunkMetres = config.chunkSize * config.voxelSize

    var burnt = 0.0
    for (key in keys) {
      val scar = scorch.scarOf(key) ?: continue
      val chunkX = (key shr 32).toInt()
      val chunkY = key.toInt()

      // The column's centre is close enough: a chunk is 32 m against a catchment of kilometres, so
      // resolving the weight per cell would be four orders of magnitude of precision nobody can see.
      val dx = (chunkX + 0.5) * chunkMetres - site.centre.x
      val dy = (chunkY + 0.5) * chunkMetres - site.centre.y
      val distance = sqrt(dx * dx + dy * dy)
      if (distance > radius) continue

      burnt += scar.visible.count * cellArea * Catchment.weightAt(distance, radius)
    }

    return burnt
  }

  /**
   * Whether a building is gone for good.
   *
   * A row whose `resumeAt` has passed is something that has already grown back and is merely still
   * written down, so it is not damage. Applied here rather than evicted, for the reason in the class
   * note - eviction is not this class's to do.
   */
  private fun isDestroyed(propId: Long): Boolean {
    val entry = divergence.of(propId) ?: return false
    val resumeAt = entry.resumeAt ?: return true

    return resumeAt.isAfter(Instant.now())
  }

  private fun economyParams() = settings.paramsFor(worldService.record.previousWinningFaction).economy

  companion object {
    /** I20: burnt ground may take at most this much of a trade's capacity, however long the siege. */
    const val SCORCH_CEILING = 0.6

    /** I20: and destroyed workplaces at most this much. */
    const val WORKPLACE_CEILING = 0.8

    /**
     * How stale an assessment may get. The books move in game-days and the shortest is twenty real
     * minutes, so half a minute is far inside what anybody could observe - and it keeps a catch-up of
     * thirty sub-steps from walking the scars thirty times.
     */
    private const val RECHECK_NANOS = 30_000_000_000L

    /** A city's, and the widest any tier reaches - the coarse gate for "is this fire anybody's". */
    private const val LARGEST_CATCHMENT_METRES = 20_000.0

    private val LOG = KotlinLogging.logger { }
  }
}
