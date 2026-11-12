package net.bestia.zone.ground

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.OffRoad
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.stream.ChunkStreamSystem
import org.springframework.stereotype.Component as SpringComponent

/**
 * Marks a Trailblazer [OffRoad] while it walks ground nobody made, so its speed is rebuilt once when it leaves or
 * joins a road rather than on every step.
 */
@SpringComponent
class OffRoadSystem(
  private val madeGround: MadeGround,
) : System {
  override val phase = Phase.WORLD
  override val after = setOf(ChunkStreamSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(CHECK_SECONDS)

  override val reads: ComponentClassSet = setOf(Master::class, Position::class, CombatStats::class)
  override val writes: ComponentClassSet = setOf(OffRoad::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Master::class, Position::class, CombatStats::class).each { id ->
      val isTrailblazer = get<CombatStats>().values.offRoadSpeedPercent > 0
      val wasOffRoad = world.has(id, OffRoad::class)

      val isOffRoad = if (isTrailblazer) {
        val made = madeGround.isMadeAt(get<Position>().toVec3L()) ?: return@each
        !made
      } else {
        false
      }
      if (isOffRoad == wasOffRoad) return@each

      if (isOffRoad) world.add(id, OffRoad) else world.remove(id, OffRoad::class)
      world.add(id, IsStatusValueDirty)
    }
  }

  private companion object {
    const val CHECK_SECONDS = 0.5f
  }
}
