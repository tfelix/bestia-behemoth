package net.bestia.zone.account

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.ecs.WorldAcessor
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.persistence.PersistAndRemove
import net.bestia.zone.ecs.session.ConnectionInfoService
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
  private val zoneServer: ZoneServer
) {

  class AddPersistAndRemoveWriter(
    private val entity: Entity
  ) : WorldAcessor {
    override fun doWithWorld(world: World) {
      with(world) {
        entity.configure {
          entity += PersistAndRemove
        }
      }
    }
  }

  /**
   * Main socket server event when a new account got connected.
   */
  @EventListener
  fun handleAccountConnected(event: AccountConnectedEvent) {
    // There is not much to do right now because we are now connected but still in somewhat of a "limbo".
    // The client now needs to list its masters and decide to select one via the SelectMasterHandler otherwise
    // no command involving a master will work.
  }

  /**
   * Socket event when a socket is closed for whatever reason (client or server initiated) and must
   * handle the cleanup work.
   */
  @EventListener
  fun handleAccountDisconnected(event: AccountDisconnectedEvent) {
    LOG.debug { "handleAccountDisconnected account: ${event.accountId}" }

    val masterEntity = masterResolver.getSelectedMasterEntityByAccountId(event.accountId)
      ?: return

    zoneServer.accessWorld(AddPersistAndRemoveWriter(masterEntity))

    // Technically I guess it would be better if the session only gets deactivated if the entity was confirmed removed
    // from the ecs...
    connectionInfoService.deactivateSession(event.accountId)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
