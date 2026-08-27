package net.bestia.zone.account

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.persistence.PersistAndRemove
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.NoActiveSessionException
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.respawn.Respawn
import net.bestia.zone.ecs.respawn.SavePointService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * This service listens if a player connects or disconnects and will create or delete all player
 * related entities.
 *
 * TODO when a gateway server is in front it probably makes sense to have either have this logic on the gateway
 *   service or probably better the gateway sends messages to the zones/shards to let the entities get spawned
 *   there.
 */
@Service
class AccountEntityControlService(
  private val connectionInfoService: ConnectionInfoService,
  private val masterResolver: MasterResolver,
  private val savePointService: SavePointService,
  private val world: WorldView
) {

  /**
   * Main socket server event when a new account got connected.
   */
  @EventListener
  fun handleAccountConnected(event: AccountConnectedEvent) {
    // Remember the authorities established during authentication so they are available once the
    // player selects a master and the session gets activated.
    connectionInfoService.registerAuthenticatedConnection(event.accountId, event.authorities)

    // Other than that there is not much to do right now because we are now connected but still in
    // somewhat of a "limbo". The client now needs to list its masters and decide to select one via
    // the SelectMasterHandler otherwise no command involving a master will work.
  }

  /**
   * Socket event when a socket is closed for whatever reason (client or server initiated) and must
   * handle the cleanup work.
   */
  @EventListener
  fun handleAccountDisconnected(event: AccountDisconnectedEvent) {
    LOG.debug { "handleAccountDisconnected account: ${event.accountId}" }

    val masterEntity = masterResolver.getSelectedMasterEntityIdByAccountId(event.accountId)
      ?: return

    // Before deactivateSession, which is what makes the session's owned entities unreachable.
    respawnDeadOwnedBestias(event.accountId)

    world.modify(masterEntity) { id ->
      add(id, PersistAndRemove)
    }

    // Technically I guess it would be better if the session only gets deactivated if the entity was confirmed removed
    // from the ecs...
    connectionInfoService.deactivateSession(event.accountId)
  }

  /**
   * Puts any owned bestia that died back on its feet as its owner leaves.
   *
   * The master's own dead-and-logged-out case is handled where it despawns, in
   * [net.bestia.zone.ecs.persistence.persisters.MasterEntityPersister]. A bestia has no equivalent
   * because it is never despawned on disconnect at all - it simply stays in the live world - so
   * without this its corpse would still be lying there when the owner comes back, with no way to
   * revive it.
   */
  private fun respawnDeadOwnedBestias(accountId: Long) {
    val masterId = try {
      connectionInfoService.getMasterId(accountId)
    } catch (_: NoActiveSessionException) {
      return
    }

    connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
      .filter { world.has(it.entityId, Dead::class) }
      .forEach { owned ->
        val savePoint = savePointService.forPlayerBestia(owned.playerBestiaId)
        world.modify(owned.entityId) { id ->
          add(id, Respawn(savePoint))
        }
      }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
