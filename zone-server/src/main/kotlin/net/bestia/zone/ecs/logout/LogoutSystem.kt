package net.bestia.zone.ecs.logout

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.persistence.PersistAndRemove
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Drives the delayed-logout countdown. Every tick it advances each [LogoutIntent] and, on the tick it
 * elapses, finalises the logout: tells the owner their master vanished (the client uses that as the
 * "logout complete" signal to run its queued action), deactivates the session, and tags the entity
 * [PersistAndRemove] so the existing persistence path saves-then-despawns it.
 *
 * Ordered before [net.bestia.zone.persistence.PersistAndRemoveSystem] (a later phase) so the tag is picked
 * up on the next tick. Cancellation is not handled here — it happens by removing the component (see
 * [LogoutCancelService]).
 */
@SpringComponent
class LogoutSystem(
  private val outMessageProcessor: OutMessageProcessor,
  private val connectionInfoService: ConnectionInfoService,
) : System {
  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = setOf(Account::class)
  override val writes: ComponentClassSet = setOf(LogoutIntent::class, DisconnectProtection::class, PersistAndRemove::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(LogoutIntent::class).each { id ->
      val intent = get<LogoutIntent>()
      intent.countdown(deltaTime)

      if (intent.hasElapsed()) {
        finalizeLogout(world, id)
      }
    }

    // Nobody is connected to tell, so a protected body just leaves the same way.
    world.query(DisconnectProtection::class).each { id ->
      val protection = get<DisconnectProtection>()
      protection.remainingSeconds -= deltaTime

      if (protection.remainingSeconds <= 0f) {
        world.remove(id, DisconnectProtection::class)
        world.add(id, PersistAndRemove)
      }
    }
  }

  private fun finalizeLogout(world: World, entityId: EntityId) {
    LOG.debug { "Logout countdown elapsed for entity $entityId, despawning" }

    // TODO This is not ideal. We need to check if the VanishEntitySMSG is not send anyways when we
    //   remove the entity via the persist and remove system?
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
