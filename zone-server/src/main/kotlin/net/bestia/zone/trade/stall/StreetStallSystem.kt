package net.bestia.zone.trade.stall

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import org.springframework.stereotype.Component as SpringComponent

/** Packs a stall up when its hour is over, or its seller walked away or left the world. */
@SpringComponent
class StreetStallSystem(
  private val stalls: StreetStalls,
) : System {
  override val phase = Phase.UPKEEP
  override val schedule: Schedule = Schedule.EverySeconds(BEAT_SECONDS)
  override val reads: ComponentClassSet = setOf(Position::class)
  override val writes: ComponentClassSet = setOf(StreetStall::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(StreetStall::class).each { stallId ->
      val stall = get<StreetStall>()
      stall.remainingSeconds -= deltaTime

      val seller = world.get(stall.sellerEntityId, Position::class)?.toVec3L()
      val walkedAway = seller == null || seller.distance(stall.at) > StreetStalls.WALK_AWAY_TILES
      if (stall.remainingSeconds <= 0f || walkedAway) {
        // Taken off first, so the next beat cannot close it a second time while the goods go back.
        world.remove(stallId, StreetStall::class)
        stalls.close(stallId, stall)
      }
    }
  }

  private companion object {
    const val BEAT_SECONDS = 1f
  }
}
