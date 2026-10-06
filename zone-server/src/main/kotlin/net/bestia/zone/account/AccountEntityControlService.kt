package net.bestia.zone.account

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ai.ecs.PlayerControlled
import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.battle.attack.AttackCancelService
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.status.InCombat
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.ecs.logout.DisconnectProtection
import net.bestia.zone.ecs.logout.LogoutIntent
import net.bestia.zone.ecs.persistence.PersistAndRemove
import net.bestia.zone.ecs.respawn.Respawn
import net.bestia.zone.ecs.respawn.SavePointService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.core.annotation.Order
import net.bestia.zone.session.AccountConnectedEvent
import net.bestia.zone.session.AccountDisconnectedEvent

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
  private val attackCancelService: AttackCancelService,
  private val zoneConfig: ZoneConfig,
  private val playerAOIService: ActivePlayerAOIService,
  private val world: WorldView
) {

  /**
   * Main socket server event when a new account got connected.
   */
  @EventListener
  @Order(AccountConnectedEvent.ListenerOrder.ENTITY_CONTROL)
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
  @Order(AccountDisconnectedEvent.ListenerOrder.ENTITY_CONTROL)
  fun handleAccountDisconnected(event: AccountDisconnectedEvent) {
    LOG.debug { "handleAccountDisconnected account: ${event.accountId}" }

    val masterEntity = masterResolver.getSelectedMasterEntityIdByAccountId(event.accountId)
    if (masterEntity != null) {
      // Before the session goes, which is what makes the session's owned entities unreachable.
      settleOwnedBestias(event.accountId)

      // The destroy hook only clears this for an anchor, and a driven bestia is not destroyed.
      playerAOIService.removeEntityPosition(event.accountId)

      world.modify(masterEntity) { id ->
        // Mid-fight the body stays as long as the logout button would have kept it, or disconnecting would be
        // the quicker way out of a fight.
        if (has(id, InCombat::class)) {
          remove(id, LogoutIntent::class)
          add(id, DisconnectProtection(zoneConfig.logoutProtectionSeconds))
        } else {
          add(id, PersistAndRemove)
        }
      }
    }

    // Always, or every account that ever connected keeps a session. Its bestias stay in the world and are
    // found again through their OwnedBestia when the master is next selected.
    connectionInfoService.removeSession(event.accountId)
  }

  /**
   * Leaves every owned bestia in a state its owner can come back to: standing orders dropped, the player's
   * control and view anchor handed back, and any corpse put back on its feet. The master picks the anchor up
   * again when it next spawns.
   *
   * A bestia is never despawned on disconnect - it simply stays in the live world - so anything left on it
   * outlives the session. A corpse would still be lying there with no way to revive it, and a standing attack
   * order would have it fighting on with nobody driving it. The master needs neither: it despawns, and its own
   * dead-and-logged-out case is handled in
   * [net.bestia.zone.ecs.persistence.persisters.MasterEntityPersister].
   */
  private fun settleOwnedBestias(accountId: Long) {
    val masterId = try {
      connectionInfoService.getMasterId(accountId)
    } catch (_: NoActiveSessionException) {
      return
    }

    connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
      .forEach { owned ->
        val isDead = world.modify(owned.entityId) { id ->
          attackCancelService.cancelAttack(this, id)
          remove(id, PlayerControlled::class)
          remove(id, ActivePlayer::class)
          has(id, Dead::class)
        } ?: false

        if (!isDead) {
          return@forEach
        }

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
