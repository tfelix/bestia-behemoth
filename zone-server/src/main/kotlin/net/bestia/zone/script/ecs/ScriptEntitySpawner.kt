package net.bestia.zone.script.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.Persistent
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Spawns a persistent entity carrying a [ScriptComponent] at a fixed world position. */
@Component
class ScriptEntitySpawner {

  fun spawnScript(
    world: World,
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
