package net.bestia.zone.ecs.place

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps every [Place] in step with where its entity is standing.
 *
 * ### Every tick, gated on movement
 *
 * A label that arrives a second after the border does not feel like the world telling you where you are.
 * `WeatherSystem` runs on an interval because the sky genuinely changes slowly; a place name changes the
 * instant a foot crosses a line, so this runs on the tick and the client has the new name one sync later.
 *
 * What makes that affordable is that `Position` already tracks whether it moved. An entity that stood
 * still costs one boolean read. A `Position` is born dirty, so an entity that has just been given one is
 * resolved on its first tick without needing a special case.
 *
 * ### It never adds a component
 *
 * The query is `Position` **and** [Place], so whatever creates an entity decides whether it has a place at
 * all - `MasterEntitySpawner` does, for a player. A monster does not need to be told where it is, and a
 * system that added the component to everything with a position would put one on every rabbit in the world.
 *
 * ### Ordering
 *
 * `@Order(47)` puts this after `MoveSystem` (40) and after `ChunkStreamSystem` (45), which writes
 * `Position` when it applies a teleport. Both are conflicts the scheduler resolves into earlier waves, so
 * this sees the tick's final position rather than depending on registration luck - and before
 * `ZoneEngine` clears the dirty flags at the end of the tick.
 */
@SpringComponent
@Order(47)
class PlaceSystem(
  private val names: PlaceNameService
) : System {

  override val schedule: Schedule
    get() {
      return Schedule.EveryTick
    }

  override val reads: ComponentClassSet = setOf(Position::class)

  override val writes: ComponentClassSet = setOf(Place::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Position::class, Place::class).each {
      val position = get<Position>()
      if (!position.isDirty()) return@each

      get<Place>().place = names.resolve(position.x, position.y)
    }
  }
}
