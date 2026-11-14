package net.bestia.zone.treasure

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.treasure.TreasureCache
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.dialog.DialogId
import net.bestia.zone.dialog.DialogService
import net.bestia.zone.geometry.Compass
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Senses the treasure caches still buried around the caster. The ground above each shimmers for the caster, and
 * a popup says how many there are and where the nearest lies. Rank buys reach, then the depth, then the richness.
 *
 * Nothing found is a reading too, like an empty Read Tracks, so it is a popup and not an error.
 */
@Component
class TreasureSense(
  private val registry: TreasureCacheRegistry,
  private val richness: TreasureRichness,
  private val sightings: TreasureSightings,
  private val dialogService: DialogService,
  private val outMessageProcessor: OutMessageProcessor,
) : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val accountId = ctx.world.accountIdOf(ctx.casterId)
    val centre = ctx.world.positionOf(ctx.casterId)
    if (accountId == null || centre == null) {
      LOG.debug { "Entity ${ctx.casterId} has no account or position to sense treasure for" }
      return null
    }

    val level = ctx.skillLevel.coerceAtLeast(1)
    val radius = RADIUS_PER_LEVEL * level
    val x = centre.x + HALF_VOXEL
    val y = centre.y + HALF_VOXEL
    val caches = registry.buriedNear(x, y, radius).filter { abs(it.z - centre.z) <= radius }

    ctx.world.masterIdOf(ctx.casterId)?.let { sightings.record(it, caches) }

    val nearest = caches.firstOrNull()
    if (nearest == null) {
      dialogService.send(accountId, DialogId.TREASURE_NONE)
      return null
    }

    outMessageProcessor.sendToPlayer(accountId, TreasureShimmerSMSG(caches.map(::spotOf), SHIMMER_SECONDS))
    dialogService.send(accountId, dialogFor(level), readingOf(nearest, caches.size, x, y, level))

    return null
  }

  private fun dialogFor(level: Int): DialogId {
    return when {
      level >= RICHNESS_LEVEL -> DialogId.TREASURE_SENSED_RICHNESS
      level >= DEPTH_LEVEL -> DialogId.TREASURE_SENSED_DEPTH
      else -> DialogId.TREASURE_SENSED
    }
  }

  private fun readingOf(nearest: TreasureCache, count: Int, x: Double, y: Double, level: Int): Map<String, DialogArg> {
    val reading = mutableMapOf(
      "count" to DialogArg.Number(count.toLong()),
      "heading" to DialogArg.Token(Compass.tokenOf(nearest.x - x, nearest.y - y)),
      "distance" to DialogArg.Number(hypot(nearest.x - x, nearest.y - y).roundToLong()),
    )
    if (level >= DEPTH_LEVEL) reading["depth"] = DialogArg.Number(nearest.depth.roundToLong().coerceAtLeast(1))
    if (level >= RICHNESS_LEVEL) reading["richness"] = DialogArg.Token("TREASURE_TIER_${richness.of(nearest).name}")
    return reading
  }

  /** The ground above the cache, where the shimmer shows. */
  private fun spotOf(cache: TreasureCache): Vec3L {
    return Vec3L(floor(cache.x).toLong(), floor(cache.y).toLong(), cache.floor.roundToLong())
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val RADIUS_PER_LEVEL = 20.0
    const val DEPTH_LEVEL = 3
    const val RICHNESS_LEVEL = 5
    const val SHIMMER_SECONDS = 60f
    const val HALF_VOXEL = 0.5
  }
}
