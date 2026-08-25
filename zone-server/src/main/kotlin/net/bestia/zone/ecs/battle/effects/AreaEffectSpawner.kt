package net.bestia.zone.ecs.battle.effects

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Puts an [AreaEffect] into the world as an entity of its own.
 *
 * Deliberately without [net.bestia.zone.ecs.persistence.Persistent]: a spell effect that outlived a
 * restart would be a bug, and leaving the marker off keeps it out of the persistence sweep by
 * construction. The vanish when [AreaEffectSystem] destroys it needs no message either - `ZoneEngine`
 * broadcasts one for any entity that had a `Dirtyable` component, which [EntityVisual] is.
 */
@Component
class AreaEffectSpawner {

  /**
   * @param visualId the effect visual to draw, or **null** for an effect that must not be drawn at all.
   *   Null is for something whose appearance already reaches the client another way - a grass fire, which is
   *   rendered from the per-chunk burning mask, so an `EntityVisual` here would draw a second fire on top of
   *   the real one. Not a default: "no visual" is a deliberate decision at every call site, and an effect that
   *   forgot its visual would otherwise be invisible for the same reason and by accident.
   */
  fun spawn(world: World, center: Vec3L, visualId: Long?, effect: AreaEffect): EntityId {
    LOG.debug {
      "Spawning area effect for skill ${effect.skillId} on $center: ${effect.remainingTicks} ticks of " +
          "${effect.damagePerTick} every ${effect.tickIntervalSeconds}s over ${effect.radiusTiles} tiles"
    }

    return world.createEntity { id ->
      add(id, Position.fromVec3(center))
      // `EntityVisual` is the only Dirtyable here, so an effect without one is also the one that gets no
      // vanish broadcast when it dies - which is right: nothing was told it appeared either.
      visualId?.let { add(id, EntityVisual(VisualKind.EFFECT, it)) }
      add(id, effect)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
