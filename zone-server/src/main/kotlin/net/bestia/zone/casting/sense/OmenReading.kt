package net.bestia.zone.casting.sense

import net.bestia.worldgen.place.PlaceRegion
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.PlaceRegionService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.mana.ManaTier
import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Augury: how restless the land is, by place region. Level 1 reads the caster's own region, level 2 also tells
 * whether a neighbouring region is worse, level 3 reads every neighbour by name. Never a direction or distance for
 * the caster's own ground: that is what Observation is for.
 *
 * A region reads by the mana in most of it, so one hot cell does not make a whole valley ominous, and a world event
 * running in it makes it read ominous whatever the mana says.
 */
@Component
class OmenReading(
  private val placeRegions: PlaceRegionService,
  private val mana: ManaConcentrationService,
  private val worldService: WorldService,
  private val messages: OutMessageProcessor,
  private val brewing: BrewingEvents? = null,
) {

  enum class Reading { CALM, UNEASY, OMINOUS, WORSE_NEARBY }

  fun read(world: World, casterId: EntityId, level: Int) {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return
    val regions = placeRegions.regions
    val voxel = worldService.config.voxelSize
    val events = brewing?.positions().orEmpty()

    val here = regions.regionAt(at.x * voxel, at.y * voxel)
    val ownReading = readingOf(here, events)
    val marks = arrayListOf(
      SenseMark(SenseKind.OMEN, at.x * voxel, at.y * voxel, here.name, ownReading.ordinal.toLong(), vague = true)
    )

    if (level >= NEIGHBOURS_LEVEL) {
      val neighbours = regions.neighboursOf(here.index).map { regions.regions[it] }.filterNot { it.isWater }
      val readings = neighbours.associateWith { readingOf(it, events) }

      if (readings.values.any { it > ownReading }) {
        marks += SenseMark(SenseKind.OMEN, at.x * voxel, at.y * voxel, refId = Reading.WORSE_NEARBY.ordinal.toLong(), vague = true)
      }
      if (level >= NAMES_LEVEL) {
        readings.forEach { (region, reading) ->
          marks += SenseMark(SenseKind.OMEN, region.centre.x, region.centre.y, region.name, reading.ordinal.toLong())
        }
      }
    }

    messages.sendToPlayer(accountId, SenseMarksSMSG(marks, VISIBLE_SECONDS))
  }

  /** What most of [region] feels like: the density nine in ten of its samples stay below. */
  fun readingOf(region: PlaceRegion, events: List<Pair<Double, Double>>): Reading {
    val regions = placeRegions.regions
    if (events.any { (x, y) -> regions.indexAt(x, y) == region.index }) return Reading.OMINOUS

    val reach = sqrt(region.cellCount * regions.cellSize * regions.cellSize / PI) * REACH_MARGIN
    val samples = ArrayList<Double>()
    var y = region.centre.y - reach
    while (y <= region.centre.y + reach) {
      var x = region.centre.x - reach
      while (x <= region.centre.x + reach) {
        if (regions.indexAt(x, y) == region.index) samples += mana.densityAt(Vec3L(x.toLong(), y.toLong(), 0))
        x += SAMPLE_METRES
      }
      y += SAMPLE_METRES
    }
    if (samples.isEmpty()) return Reading.CALM

    samples.sort()
    val tier = ManaTier.of(samples[(samples.size * MOSTLY).toInt().coerceAtMost(samples.size - 1)])
    return when {
      tier >= ManaTier.OMINOUS -> Reading.OMINOUS
      tier >= ManaTier.UNEASY -> Reading.UNEASY
      else -> Reading.CALM
    }
  }

  private companion object {
    const val NEIGHBOURS_LEVEL = 2
    const val NAMES_LEVEL = 3
    const val VISIBLE_SECONDS = 60

    /** A region is not a disc, so the sampled square reaches a little past the disc of its area. */
    const val REACH_MARGIN = 1.6
    const val SAMPLE_METRES = 512.0
    const val MOSTLY = 0.9
  }
}
