package net.bestia.zone.worldevent.seal

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.worldboss.WorldBossSealService
import net.bestia.zone.worldevent.EventGround
import net.bestia.zone.worldevent.rift.SealStones
import org.springframework.stereotype.Component

/** Every seal still standing: the crystals nobody broke and the seal stones. Off the tick. */
@Component
class OldSeals(
  private val crystals: WorldBossSealService,
  private val divergence: WorldObjectDivergenceRegistry,
  private val stones: SealStones,
  private val ground: EventGround,
  private val world: WorldView,
) {

  fun all(): List<OldSeal> = standingCrystals() + world.read { stones.all() }.map { OldSeal.Stone(it, Vec3L(it.x, it.y, it.z)) }

  fun of(subjectId: Long): OldSeal? = all().firstOrNull { it.subjectId == subjectId }

  private fun standingCrystals(): List<OldSeal.Crystal> =
    crystals.all()
      .filter { divergence.of(it.propId) == null }
      .mapNotNull { crystal -> ground.grounded(crystal.at.x, crystal.at.y)?.let { OldSeal.Crystal(crystal.propId, crystal.bossId, it) } }
}
