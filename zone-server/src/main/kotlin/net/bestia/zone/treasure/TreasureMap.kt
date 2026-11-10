package net.bestia.zone.treasure

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Charts the nearest cache the caster sensed in the last ten minutes onto a sheet of blank vellum. The map shows
 * a circle the cache lies somewhere inside; rank shrinks it, and it is rolled once, when the map is drawn.
 */
@Component
class TreasureMap(
  private val sightings: TreasureSightings,
  private val caches: TreasureCacheRegistry,
  private val maps: TreasureMapService,
  private val outMessageProcessor: OutMessageProcessor,
  private val random: Random = ThreadLocalRandomSource,
) : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val masterId = ctx.world.masterIdOf(ctx.casterId) ?: return null
    val accountId = ctx.world.accountIdOf(ctx.casterId) ?: return null
    val position = ctx.world.positionOf(ctx.casterId) ?: return null

    val cache = sightings.recentNear(masterId, position.x.toDouble(), position.y.toDouble())
      .firstOrNull { caches.progressOf(it.id)?.opened != true }
    if (cache == null) {
      outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.TREASURE_MAP_NOTHING_SENSED))
      return null
    }

    val radius = RADIUS_BY_LEVEL[(ctx.skillLevel.coerceIn(1, RADIUS_BY_LEVEL.size)) - 1]
    // Anywhere in the circle, evenly, so the cache is not likelier near its middle.
    val offset = radius * sqrt(random.nextDouble())
    val angle = 2.0 * PI * random.nextDouble()
    maps.draw(ctx.casterId, masterId, accountId, cache, cache.x - offset * cos(angle), cache.y - offset * sin(angle), radius)

    return null
  }

  private companion object {
    val RADIUS_BY_LEVEL = doubleArrayOf(50.0, 25.0, 0.0)
  }
}
