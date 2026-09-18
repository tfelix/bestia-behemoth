package net.bestia.zone.world.ground

import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import kotlin.math.sqrt

/**
 * Where something has bled.
 *
 * Everything about holding, fading and writing the grid is [GroundLevelStore]'s. What is here is the one thing
 * blood does not share with the other graded layers: it is **soaked in once** rather than accumulated. A death
 * writes a pool and that pool only ever dries - which is why [spillWeight] is most of a full cell where a
 * footfall is a few parts in a hundred.
 *
 * ### Ground type does not come into it
 *
 * Wear asks what the ground is made of, because grass wears and cobblestone does not. Blood does not care: it
 * pools on stone and soaks into soil, and a battle fought on a road should leave a road that has been fought
 * on. So there is no `CAP_` table here and there should not be one.
 */
@Service
class GroundBloodRegistry(
  repository: GroundLayerMarkRepository,
  asyncJobExecutor: AsyncJobExecutor,
  worldService: WorldService,
  private val config: GroundBloodConfig,
  clock: BestiaClock,
) : GroundLevelStore(repository, asyncJobExecutor, worldService, config, clock, GroundLayer.BLOODIED) {

  private val spillWeight get() = config.spillWeight

  /**
   * Soaks a pool into the ground around one tile.
   *
   * @return the columns whose stains a client would now draw differently. Per column rather than one for the
   *   centre, because a pool two metres across reaches over a chunk seam every thirty-two metres and
   *   announcing only the middle would leave the far half a beat behind.
   */
  fun spill(voxelX: Long, voxelY: Long, nowSecond: Long): Set<Long> {
    val radius = config.spillRadiusTiles
    val touched = mutableSetOf<Long>()

    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        val distance = sqrt((dx * dx + dy * dy).toDouble())
        if (distance > radius) continue

        // Falls to nothing just past the edge rather than at it, so the outermost ring is faint instead of
        // being a hard rim - which is what makes a pool read as one rather than as a painted disc.
        val amount = (spillWeight * (1.0 - distance / (radius + 1.0))).toInt()

        if (mark(voxelX + dx, voxelY + dy, amount, nowSecond)) {
          touched += columnKeyOf(voxelX + dx, voxelY + dy)
        }
      }
    }

    return touched
  }
}
