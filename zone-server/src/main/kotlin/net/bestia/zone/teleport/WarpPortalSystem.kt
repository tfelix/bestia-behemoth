package net.bestia.zone.teleport

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/** Closes a warp portal, both ends, when its time is over or no passage is left. */
@SpringComponent
class WarpPortalSystem : System {
  override val phase = Phase.UPKEEP
  override val schedule: Schedule = Schedule.EverySeconds(BEAT_SECONDS)
  override val writes: ComponentClassSet = setOf(WarpPortalEnd::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(WarpPortalEnd::class).each { id ->
      val end = get<WarpPortalEnd>()
      end.remainingSeconds -= deltaTime
      if (end.remainingSeconds <= 0f || end.passages.left <= 0) {
        world.destroy(id)
      }
    }
  }

  private companion object {
    const val BEAT_SECONDS = 1f
  }
}
