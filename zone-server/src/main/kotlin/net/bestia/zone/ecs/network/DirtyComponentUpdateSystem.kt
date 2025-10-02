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
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.IteratingSystem
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.status.Exp
import net.bestia.zone.ecs.status.Level
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

    val isEntityActivePlayer = entity.has(ActivePlayer::class)

    if (posComp.isDirty()) {
      aoiService.setEntityPosition(entity.id, position)

      if (isEntityActivePlayer) {
        updatePlayerAOI(entity, position)
      }
    }

    // TODO How to make sure to add other dirtyable components in here. Maybe add a unit test to check this?
    updatePublicBroadcastableDirtyComponents(entity, zone, position)
    updatePrivateBroadcastableDirtyComponents(entity, zone)

    entity.remove(IsDirty::class)
  }

  private fun updatePublicBroadcastableDirtyComponents(entity: Entity, zone: ZoneServer, position: Vec3L) {
    val broadcastUpdateMessages = listOfNotNull(
      makeMessageIfDirty(entity.id, entity.get(Position::class)),
      makeMessageIfDirty(entity.id, entity.get(Speed::class)),
      makeMessageIfDirty(entity.id, entity.get(Path::class)),
      makeMessageIfDirty(entity.id, entity.get(BestiaVisual::class)),
      makeMessageIfDirty(entity.id, entity.get(Exp::class)),
      makeMessageIfDirty(entity.id, entity.get(Level::class))
    )

    // These messages are optional, depending on if the entity is a master or not. Some messages e.g. health should
    // only be broadcasted for non player controlled entities.
    val optionalMessages = if (!entity.has(Account::class)) {
      listOfNotNull(
        makeMessageIfDirty(entity.id, entity.get(Health::class))
      )
    } else {
      emptyList()
    }

    val allMessagesToBroadcast = broadcastUpdateMessages + optionalMessages

    if (allMessagesToBroadcast.isNotEmpty()) {
      zone.queueExternalJob {
        outMessageProcessor.sendToAllPlayersInRange(
          position,
          allMessagesToBroadcast
        )
      }
    }
  }

  private fun updatePrivateBroadcastableDirtyComponents(entity: Entity, zone: ZoneServer) {
    val ownedByAccountId = entity.get(Account::class)?.accountId
      ?: return

    val broadcastUpdateMessages = listOfNotNull(
      makeMessageIfDirty(entity.id, entity.get(Exp::class)),
      makeMessageIfDirty(entity.id, entity.get(Health::class))
    )

    if (broadcastUpdateMessages.isNotEmpty()) {
      zone.queueExternalJob {
        outMessageProcessor.sendToPlayer(ownedByAccountId, broadcastUpdateMessages)
      }
    }
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
