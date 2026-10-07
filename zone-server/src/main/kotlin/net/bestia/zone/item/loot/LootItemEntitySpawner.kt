package net.bestia.zone.item.loot

import net.bestia.zone.util.EntityId
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.item.ecs.GroundItemDecay
import net.bestia.zone.item.ecs.GroundItemIntegrity
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.LootProtection
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.Persistent
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Spawns an item entity in the world which can be used to pickup.
 */
@Component
class LootItemEntitySpawner(
  private val zoneConfig: WorldRulesConfig,
  private val clock: Clock = Clock.systemUTC()
) {

  /**
   * Spawns a single ground item entity at the given position which can be picked up. A rehydrated item passes
   * the [despawnAt] and [integrityLost] it was persisted with; a fresh drop gets the full time and is unharmed.
   * Kill loot names the [lootOwner] account, which alone may pick it up for a few seconds.
   */
  fun spawnLootItem(
    world: World,
    itemId: Long,
    amount: Int,
    pos: Vec3L,
    uniqueId: Long = 0,
    entityId: EntityId? = null,
    despawnAt: Instant? = null,
    integrityLost: Int = 0,
    lootOwner: Long? = null,
  ): EntityId {
    val configure: World.(EntityId) -> Unit = { id ->
      add(id, Position.fromVec3(pos))
      add(id, EntityVisual(VisualKind.ITEM, itemId))
      add(
        id,
        GroundItemStack(
          itemId = itemId,
          amount = amount,
          uniqueId = uniqueId
        )
      )
      add(id, GroundItemIntegrity(lost = integrityLost))
      lootOwner?.let { add(id, LootProtection(it, zoneConfig.lootProtectionSeconds)) }
      add(id, Persistent)
      // A unique item is one of a kind, and its instance would be lost with it, so only plain items decay.
      if (uniqueId == 0L) {
        add(id, GroundItemDecay(despawnAt ?: clock.instant().plus(zoneConfig.groundItemDespawnAfter)))
      }
    }

    // Rehydrated ground items keep their persisted id; freshly dropped ones get a new one.
    return if (entityId != null) world.createEntity(entityId, configure) else world.createEntity(configure)
  }
}
