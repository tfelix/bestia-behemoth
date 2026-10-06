package net.bestia.zone.aoi

import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.ecs.core.World
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
) {

  /**
   * The player's own account is added because the chunk it walks into may not have been sent yet, and losing
   * sight of yourself for those ticks is never the right answer. Tick thread, or under the world lock.
   */
  fun of(world: World, entityId: EntityId): Set<AccountId> {
    val observers = entityVisibility.observersOf(entityId)

    if (!world.has(entityId, ActivePlayer::class)) return observers

    val owner = world.get(entityId, Account::class)?.accountId ?: return observers

    return if (owner in observers) observers else observers + owner
  }
}
