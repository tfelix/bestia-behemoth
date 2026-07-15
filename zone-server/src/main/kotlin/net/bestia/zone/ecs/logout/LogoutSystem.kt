package net.bestia.zone.ecs.logout

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.persistence.PersistAndRemove
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Drives the delayed-logout countdown. Every tick it advances each [LogoutIntent] and, on the tick it
 * elapses, finalises the logout: tells the owner their master vanished (the client uses that as the
 * "logout complete" signal to run its queued action), deactivates the session, and tags the entity
 * [PersistAndRemove] so the existing persistence path saves-then-despawns it.
 *
 * Ordered before [net.bestia.zone.ecs.persistence.PersistAndRemoveSystem] (@90) so the tag is picked
 * up on the next tick. Cancellation is not handled here — it happens by removing the component (see
 * [LogoutService]).
 */
@SpringComponent
@Order(85)
class LogoutSystem(
  private val outMessageProcessor: OutMessageProcessor,
  private val connectionInfoService: ConnectionInfoService,
) : System {

  override val reads: ComponentClassSet = setOf(Account::class)
  override val writes: ComponentClassSet = setOf(LogoutIntent::class, PersistAndRemove::class)

  override fun update(world: World, deltaTime: Float) {
    val elapsed = mutableListOf<EntityId>()

    world.query(LogoutIntent::class).each { id ->
      val intent = get<LogoutIntent>()
      if (intent.tick(deltaTime)) {
        elapsed.add(id)
      }
    }

    for (id in elapsed) {
      finalizeLogout(world, id)
    }
  }

  private fun finalizeLogout(world: World, entityId: EntityId) {
    LOG.debug { "Logout countdown elapsed for entity $entityId, despawning" }

    val accountId = world.get(entityId, Account::class)?.accountId
    if (accountId != null) {
      // The owner treats their own master vanishing as "logout complete".
      outMessageProcessor.sendToPlayer(
        accountId,
        VanishEntitySMSG(entityId = entityId, kind = VanishEntitySMSG.VanishKind.GONE)
      )
      connectionInfoService.deactivateSession(accountId)
    }

    // Reuse the standard persist-then-remove path (picked up next tick by PersistAndRemoveSystem).
    world.add(entityId, PersistAndRemove)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
