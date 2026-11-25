package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.world.stream.ChunkStreamSystem
import java.time.Clock
import org.springframework.stereotype.Component as SpringComponent

/**
 * Removes plain items that lay on the ground too long. Every one is an entity that is streamed, persisted and
 * loaded again on every start, so without this a player dropping items one by one could fill the world.
 *
 * Destroyed like a picked-up stack: the destroy tells the clients, and the persisted row is pruned with it.
 */
@SpringComponent
class GroundItemDecaySystem(
  private val groundStackRemoval: GroundStackRemoval,
  private val clock: Clock = Clock.systemUTC(),
  private val manaRelease: GroundStackManaRelease? = null,
) : System {

  override val phase = Phase.WORLD
  override val schedule: Schedule = Schedule.EverySeconds(60f)
  override val reads: ComponentClassSet = manaRelease?.reads.orEmpty()

  // Where a crumbling crystal lay is read before the streamer moves anything this tick.
  override val before = setOf(ChunkStreamSystem::class)
  override val writes: ComponentClassSet = setOf(GroundItemDecay::class)

  override fun update(world: World, deltaTime: Float) {
    val now = clock.instant()

    world.query(GroundItemDecay::class).each { id ->
      if (!now.isBefore(get<GroundItemDecay>().despawnAt)) {
        manaRelease?.release(world, id)
        groundStackRemoval.remove(world, id)
      }
    }
  }
}
