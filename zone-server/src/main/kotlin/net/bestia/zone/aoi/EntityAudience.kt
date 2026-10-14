package net.bestia.zone.aoi

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Who is told about an entity: the accounts that see it, plus always the account that plays it.
 *
 * One rule for component state and one-off events alike, so a client never sees a health drop without the hit.
 */
@Component
class EntityAudience(
  private val entityVisibility: EntityVisibility,
  private val sessions: ConnectionInfoService,
) {

  /**
   * The player hears about their own master and bestias wherever these stand, so the map can show them, and so
   * the entity they drive is not lost while the chunk it walks into is not sent yet. Tick thread, or under the
   * world lock.
   */
  fun of(world: World, entityId: EntityId): Set<AccountId> {
    val observers = entityVisibility.observersOf(entityId)
    val owner = ownerOf(world, entityId) ?: return observers

    return if (owner in observers) observers else observers + owner
  }

  /**
   * The account playing [entityId] right now, as its master or one of that master's bestias. Null for anything
   * else, and for a bestia whose owner logged out or plays another master.
   */
  fun ownerOf(world: World, entityId: EntityId): AccountId? {
    val accountId = world.get(entityId, Account::class)?.accountId ?: return null
    val masterId = world.get(entityId, Master::class)?.masterId
      ?: world.get(entityId, OwnedBestia::class)?.masterId
      ?: return null

    return if (sessions.activeMasterIdOf(accountId) == masterId) accountId else null
  }

  companion object {
    /** What [of] reads, for a system that addresses a message with it. */
    val READS: ComponentClassSet = setOf(Account::class, Master::class, OwnedBestia::class)
  }
}
