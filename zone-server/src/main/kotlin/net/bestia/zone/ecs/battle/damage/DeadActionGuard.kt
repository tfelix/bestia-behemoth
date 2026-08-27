package net.bestia.zone.ecs.battle.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Refuses player actions coming from a body that is waiting to respawn.
 *
 * Needed because a dead player-owned entity is still alive in the ECS sense - it keeps its
 * components and its session still resolves to it - so every handler that used to be safe by virtue
 * of the entity being destroyed now has to ask explicitly.
 *
 * Deliberately silent towards the client. An honest one has the death window up and offers none of
 * these actions, so a request arriving anyway is a client bug or a hand-crafted packet, not a
 * refusal a player is meant to read - see the `error-messages` skill on not minting an `OpError`
 * value nobody legitimate will ever see.
 */
@Service
class DeadActionGuard(
  private val world: WorldView,
) {

  /** True when [entityId] is dead, in which case the caller must abandon [action]. */
  fun refuses(entityId: EntityId, action: String): Boolean {
    if (!world.has(entityId, Dead::class)) {
      return false
    }

    LOG.warn { "Entity $entityId tried to $action while dead, ignoring" }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
