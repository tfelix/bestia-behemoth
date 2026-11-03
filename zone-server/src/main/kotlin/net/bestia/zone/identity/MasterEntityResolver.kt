package net.bestia.zone.identity

import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** The master entity that answers for an entity: what it earns, and what it is blamed for. */
@Component
class MasterEntityResolver(
  private val sessions: ConnectionInfoService,
) {

  /**
   * A master is its own, and a bestia's is its owner's while the owner plays the master it belongs to. Null for
   * anything else.
   */
  fun masterEntityOf(world: World, entityId: EntityId): EntityId? {
    if (world.has(entityId, Master::class)) {
      return entityId
    }

    val accountId = world.get(entityId, Account::class)?.accountId ?: return null
    val ownedBestia = world.get(entityId, OwnedBestia::class) ?: return null
    if (!sessions.isPlaying(accountId, ownedBestia.masterId)) {
      return null
    }

    return selectedMasterEntityOf(accountId)
  }

  fun selectedMasterEntityOf(accountId: AccountId): EntityId? {
    return try {
      sessions.getSelectedMasterEntityId(accountId)
    } catch (_: NoActiveSessionException) {
      null
    }
  }
}
