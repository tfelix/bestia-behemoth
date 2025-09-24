package net.bestia.zone.ecs.network

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.EntityRegistry
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.message.entity.EntitySMSG
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.util.EntityId

/**
 * Responsible for filtering every entity which needs to be sent over the network as fast as possible.
 * These are entities with animations or position updates for example.
 * We also use this step to update different systems like the area of interest services.
 */
class DirtyComponentUpdateSystem(
  private val sender: OutMessageProcessor = inject(),
  private val entityRegistry: EntityRegistry = inject(),
  private val aoiService: EntityAOIService = inject(),
) : IteratingSystem(
  family = family {
    all(Position, IsDirty)
  },
) {

  override fun onTickEntity(entity: Entity) {
    val entityId = entityRegistry.getEntityId(entity)

    if (entityId == null) {
      LOG.error { "Entity was not found in the registry: ${entity.id}, can not update clients but it is dirty." }
    } else {
      performUpdates(entityId, entity)
    }

    entity.configure {
      it -= IsDirty
    }
  }

  private fun performUpdates(entityId: EntityId, entity: Entity) {
    val posComp = entity[Position]

    if (posComp.isDirty()) {
      // TODO evaluate at some point if we could benefit if we use the ecs entity id instead to
      //   avoid a lot of lookups. Atm its not clear from what direction its more often used.
      aoiService.setEntityPosition(entityId, posComp.toVec3L())
    }

    val speedComp = entity.getOrNull(Speed)
    val pathComp = entity.getOrNull(Path)
    // TODO add other dirtyable components in here. Maybe add a unit test to check this?
    val updateMessages = listOfNotNull(
      makeMessageIfDirty(entityId, posComp),
      makeMessageIfDirty(entityId, speedComp),
      makeMessageIfDirty(entityId, pathComp)
    )

    sender.sendToAllPlayersInRange(
      posComp.toVec3L(),
      updateMessages
    )

    // Components updates were send out, clear the dirty status
    posComp.clearDirty()
    pathComp?.clearDirty()
    speedComp?.clearDirty()
  }

  private fun makeMessageIfDirty(entityId: EntityId, dirtyable: Dirtyable?): EntitySMSG? {
    if (dirtyable == null) {
      return null
    }

    return if (dirtyable.isDirty()) {
      dirtyable.toEntityMessage(entityId)
    } else {
      null
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
