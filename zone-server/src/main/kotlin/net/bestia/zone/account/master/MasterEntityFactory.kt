package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs.player.ActivePlayer
import net.bestia.zone.ecs.player.Master as MasterComponent
import net.bestia.zone.ecs.status.Level
import net.bestia.zone.ecs.visual.MasterVisual
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.item.Inventory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Creates an entity with all the required component from a master db entity.
 */
@Component
class MasterEntityFactory(
  private val zoneServer: ZoneServer,
  private val masterRepository: MasterRepository,
) {

  @Transactional(readOnly = true)
  fun createMasterEntity(masterId: Long): EntityId {
    val master = masterRepository.findByIdOrThrow(masterId)

    LOG.info { "Create master entity for account ${master.account.id} with master id: $masterId" }

    return zoneServer.addEntityWithWriteLock { entity ->
      entity.addAll(
        Account(master.account.id),
        MasterComponent(master.id),
        Position.fromVec3(master.position),
        Level(master.level),
        Speed(),
        Health(
          current = 10,
          max = 10
        ),
        MasterVisual(
          id = master.id.toInt(),
          skinColor = master.skinColor,
          hairColor = master.hairColor,
          face = master.face,
          body = master.body,
          hair = master.hair
        ),
        buildInventory(master),
        IsDirty,
        ActivePlayer,
      )
    }
  }

  private fun buildInventory(master: Master): Inventory {
    return Inventory(
      items = master.inventory.items.map { invItem ->
        Inventory.Item(
          itemId = invItem.item.id.toInt(),
          amount = invItem.amount,
          uniqueId = 0
        )
      }.toMutableList()
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}