package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.PropVitality
import net.bestia.zone.ecs.prop.StaticSync
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.world.stream.ChunkStreamConfig
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import net.bestia.zone.ecs.core.System as EcsSystem

/**
 * Brings static entities into existence behind the terrain, and takes them out behind it too.
 *
 * ### Why a system rather than the callback itself
 *
 * `ChunkSubscriptionService`'s callbacks fire from inside `ChunkStreamSystem.update`, while systems are
 * iterating. `World.create` is immediate but `add` is deferred to the end of the tick, so materialising in the
 * callback would mint ids now and attach components later - benign today and fragile. The established pattern
 * is `ChunkService.onChunkChanged`'s: *"the listener runs on the tick thread inside the edit, so it must be
 * cheap and must not itself edit: mark something stale and return."*
 *
 * ### Order 46
 *
 * Immediately after `ChunkStreamSystem` (45), so the terrain a prop stands on goes out first and the props
 * follow in the same tick. Declaring the prop component types as `writes` also places it after `MoveSystem`
 * (40) in the wave computation, which matters only because `reads` names `PropPose` and a future mover might
 * touch it.
 *
 * Deliberately **before** `PersistAndRemoveSystem` (90), which is irrelevant while props carry no `Persistent`
 * - and they must not: `EntityPersistenceService`'s ninety-second sweep would snapshot and upsert every
 * resident prop, 99.99% of which are pristine.
 */
@Component
@Order(46)
class WorldObjectResidencySystem(
  private val residency: WorldObjectResidencyService,
  private val settings: ChunkStreamConfig
) : EcsSystem {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = setOf(PropPose::class)

  override val writes: ComponentClassSet = setOf(
    PropPose::class,
    StaticVisual::class,
    PropVitality::class,
    WorldObjectIdentity::class,
    StaticSync::class
  )

  override fun update(world: World, deltaTime: Float) {
    if (residency.pending == 0) return

    val (loaded, released) = residency.drain(world, settings.chunksPerTickPerPlayer)

    if (loaded > 0 || released > 0) {
      LOG.trace {
        "static entities: +$loaded columns, -$released columns, ${residency.residentEntities} resident " +
            "over ${residency.residentColumns} columns, ${residency.pending} queued"
      }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
