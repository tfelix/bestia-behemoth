package net.bestia.zone.world.ground

import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * Which ground has been walked bare.
 *
 * Everything about holding, fading and writing the grid is [GroundLevelStore]'s; what is here is the one thing
 * wear does not share with the other graded layers, which is that it **accumulates**. A footfall adds a little
 * and the level is the sum of a season of them, where blood is soaked in once and only ever fades.
 */
@Service
class GroundWearRegistry(
  repository: GroundLayerMarkRepository,
  asyncJobExecutor: AsyncJobExecutor,
  worldService: WorldService,
  config: GroundWearConfig,
  clock: BestiaClock,
) : GroundLevelStore(repository, asyncJobExecutor, worldService, config, clock, GroundLayer.WORN) {

  /**
   * Adds wear at one tile.
   *
   * Wear in a column nobody is holding is dropped rather than accumulated - creatures do wander outside every
   * view, and wear nobody can be told about is wear nobody can see. Tracks are the opposite case and are
   * recorded regardless; see `GroundStampRegistry`.
   *
   * @return true if a level the client would draw changed
   */
  fun wear(voxelX: Long, voxelY: Long, amount: Int, nowSecond: Long): Boolean {
    return mark(voxelX, voxelY, amount, nowSecond)
  }
}
