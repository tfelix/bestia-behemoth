package net.bestia.zone.prop

import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.GrowthStage
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.persistence.DivergenceState
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Plants a sapling in a tree stump, as Replanting does: it skips waiting for a grown neighbour, grows at the
 * planter's pace and may carry an extra yield for when it is felled grown. Tick thread.
 */
@Component
class TreePlanting(
  private val aoi: EntityAOIService,
  private val divergence: WorldObjectDivergenceRegistry,
  private val kinds: PropKindRegistry,
  private val residency: WorldObjectResidencyService,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** Plants in the stump within a tile of [at]. False when there is no stump there. */
  fun plant(world: World, at: Vec3L, growthPercent: Int, bonusYield: Int): Boolean {
    val (stumpId, propId) = stumpNear(world, at) ?: return false
    val entry = divergence.of(propId) ?: return false
    val regrowth = kinds.of(entry.kind).regrowth ?: return false

    val nextStage = clock.instant().plusSeconds(regrowth.stageSeconds * growthPercent / 100)
    divergence.record(propId, entry.kind, DivergenceState.SAPLING, nextStage, growthPercent, bonusYield)
    residency.replace(world, stumpId, GrowthStage.SAPLING)

    return true
  }

  private fun stumpNear(world: World, at: Vec3L): Pair<Long, Long>? {
    for (id in aoi.queryEntitiesInCube(at, STUMP_REACH_TILES * 2, AoiLayer.ALL)) {
      val propId = world.get(id, WorldObjectIdentity::class)?.propId ?: continue
      if (divergence.of(propId)?.state == DivergenceState.STUMP) return id to propId
    }

    return null
  }

  private companion object {
    const val STUMP_REACH_TILES = 1L
  }
}
