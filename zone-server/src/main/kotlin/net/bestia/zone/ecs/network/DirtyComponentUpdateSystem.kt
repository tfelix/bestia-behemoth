package net.bestia.zone.ecs.network

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.ecs.player.ActivePlayer
import net.bestia.zone.ecs.visual.BestiaVisual
import net.bestia.zone.ecs2.Dirtyable
import net.bestia.zone.ecs2.Entity
import net.bestia.zone.ecs2.IteratingSystem
import net.bestia.zone.ecs2.ZoneServer
import net.bestia.zone.geometry.Vec3L
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
  private val playerAOIService: ActivePlayerAOIService,
  private val outMessageProcessor: OutMessageProcessor
) : IteratingSystem() {
  override val requiredComponents = setOf(
    Position::class,
    IsDirty::class
  )

  override fun update(
    deltaTime: Float,
    entity: Entity,
    zone: ZoneServer
  ) {
    val posComp = entity.getOrThrow(Position::class)
    val position = posComp.toVec3L()

    if (posComp.isDirty()) {
      aoiService.setEntityPosition(entity.id, position)

      if (entity.has(ActivePlayer::class)) {
        updatePlayerAOI(entity, position)
      }
    }

    // TODO How to make sure to add other dirtyable components in here. Maybe add a unit test to check this?
    val updateMessages = listOfNotNull(
      makeMessageIfDirty(entity.id, posComp),
      makeMessageIfDirty(entity.id, entity.get(Speed::class)),
      makeMessageIfDirty(entity.id, entity.get(Path::class)),
      makeMessageIfDirty(entity.id, entity.get(BestiaVisual::class))
    )

    zone.queueExternalJob {
      outMessageProcessor.sendToAllPlayersInRange(
        position,
        updateMessages
      )
    }

    entity.remove(IsDirty::class)
  }

  private fun updatePlayerAOI(entity: Entity, position: Vec3L) {
    val account = entity.get(Account::class)
    val isActivePlayer = entity.has(ActivePlayer::class)

    if (account == null || !isActivePlayer) {
      return
    }

    LOG.trace { "Updating entity ${entity.id} (account: ${account.accountId}) AOI to $position" }

    playerAOIService.setEntityPosition(account.accountId, position)
  }

  private fun makeMessageIfDirty(entityId: EntityId, dirtyable: Dirtyable?): EntitySMSG? {
    if (dirtyable == null) {
      return null
    }

    return if (dirtyable.isDirty()) {
      dirtyable.clearDirty()
      dirtyable.toEntityMessage(entityId)
    } else {
      null
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
