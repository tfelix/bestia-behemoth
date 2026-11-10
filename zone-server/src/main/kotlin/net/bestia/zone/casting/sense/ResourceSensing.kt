package net.bestia.zone.casting.sense

import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Resource Sense: the nearest ore, rare herbs, timber and game in range, each shown off its true spot by up to
 * [MAX_ERROR_SHARE] of the distance at no accuracy, and less the more accurate the sense is.
 */
@Component
class ResourceSensing(
  private val finder: ResourceFinder,
  private val worldService: WorldService,
  private val messages: OutMessageProcessor,
  private val random: Random = Random.Default,
) {

  /** @param accuracy 0 to 1: a share of the error taken away */
  fun sense(world: World, casterId: EntityId, rangeMetres: Double, accuracy: Double) {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return
    val voxel = worldService.config.voxelSize
    val east = at.x * voxel
    val north = at.y * voxel

    val found = listOfNotNull(
      finder.nearestOre(east, north, rangeMetres),
      finder.nearestRareHerbs(east, north, rangeMetres),
      finder.nearestTimber(east, north, rangeMetres),
      finder.nearestGame(world, east, north, rangeMetres),
    )
    val shown = found.map { blurred(it, east, north, accuracy) }

    messages.sendToPlayer(accountId, SenseMarksSMSG(shown, VISIBLE_SECONDS))
  }

  private fun blurred(mark: SenseMark, east: Double, north: Double, accuracy: Double): SenseMark {
    val distance = hypot(mark.eastMetres - east, mark.northMetres - north)
    val error = distance * MAX_ERROR_SHARE * (1.0 - accuracy.coerceIn(0.0, 1.0)) * random.nextDouble()
    val angle = random.nextDouble(2 * PI)

    return mark.copy(eastMetres = mark.eastMetres + cos(angle) * error, northMetres = mark.northMetres + sin(angle) * error)
  }

  companion object {
    /** At no accuracy the spot may be off by 40% of its distance; Resource Sense Lv1 takes 10% of that away. */
    const val MAX_ERROR_SHARE = 0.4

    const val VISIBLE_SECONDS = 60
  }
}
