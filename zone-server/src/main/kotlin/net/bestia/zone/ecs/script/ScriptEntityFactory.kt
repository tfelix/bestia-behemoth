package net.bestia.zone.ecs.script

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Spawns a persistent, non-synced entity carrying a [ScriptComponent] at a fixed world position. */
@Component
class ScriptEntityFactory {

  fun createScriptEntity(
    world: WorldView,
    position: Vec3L,
    scriptId: String,
    entityId: EntityId? = null,
  ): EntityId {
    LOG.debug { "Spawning script entity '$scriptId' on $position" }

    val configure: World.(EntityId) -> Unit = { id ->
      add(id, Position.fromVec3(position))
      add(id, ScriptComponent(scriptId))
      add(id, Persistent)
    }

    // Rehydrated entities keep their persisted id; freshly spawned ones get a new one.
    return if (entityId != null) world.createEntity(entityId, configure) else world.createEntity(configure)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
