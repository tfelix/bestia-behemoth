package net.bestia.zone.ecs.network

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs2.Dirtyable
import net.bestia.zone.ecs2.Entity
import net.bestia.zone.ecs2.IteratingSystem
import net.bestia.zone.ecs2.ZoneServer
import net.bestia.zone.message.entity.EntitySMSG
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Responsible for filtering every entity which needs to be sent over the network as fast as possible.
 * These are entities with animations or position updates for example.
 * We also use this step to update different systems like the area of interest services.
 */
@Component
class DirtyComponentUpdateSystem(
  private val aoiService: EntityAOIService,
  private val outMessageProcessor: OutMessageProcessor
) : IteratingSystem(
  Position::class,
  IsDirty::class
) {

  override fun update(
    deltaTime: Long,
    entity: Entity,
    zone: ZoneServer
  ) {
    val posComp = entity.getOrThrow(Position::class)

    if (posComp.isDirty()) {
      // TODO evaluate at some point if we could benefit if we use the ecs entity id instead to
      //   avoid a lot of lookups. Atm its not clear from what direction its more often used.
      aoiService.setEntityPosition(entity.id, posComp.toVec3L())
    }

    val speedComp = entity.get(Speed::class)
    val pathComp = entity.get(Path::class)

    // TODO How to make sure to add other dirtyable components in here. Maybe add a unit test to check this?
    val updateMessages = listOfNotNull(
      makeMessageIfDirty(entity.id, posComp),
      makeMessageIfDirty(entity.id, speedComp),
      makeMessageIfDirty(entity.id, pathComp)
    )

    val position = posComp.toVec3L()
    zone.queueExternalJob {
      outMessageProcessor.sendToAllPlayersInRange(
        position,
        updateMessages
      )
    }

    // Components updates were send out, clear the dirty status
    posComp.clearDirty()
    pathComp?.clearDirty()
    speedComp?.clearDirty()

    entity.remove(IsDirty::class)
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
