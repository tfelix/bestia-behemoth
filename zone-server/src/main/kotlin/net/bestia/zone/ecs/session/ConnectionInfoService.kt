package net.bestia.zone.ecs.session

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.MasterEntityId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * This service keeps track of the current master and its entity id and selected entity
 * ID of a player.
 *
 * TODO this should later probably also be in the Redis service?
 */
@Service
class ConnectionInfoService {

  private sealed class Session {
    abstract val playerEntitiesByMaster: MutableMap<Long, MutableSet<PlayerEntity>>
  }

  private data class ActiveConnection(
    override val playerEntitiesByMaster: MutableMap<Long, MutableSet<PlayerEntity>>,
    val master: MasterEntity,
    val authorities: Set<Authority>,
    /**
     * The entity the player currently has focused and which is used as the base for
     * getting client updates of all of its surroundings.
     */
    var currentActiveEntity: EntityId,
  ) : Session() {

    fun deactivate(): InactiveConnection {
      return InactiveConnection(
        playerEntitiesByMaster = playerEntitiesByMaster
      )
    }
  }

  private data class InactiveConnection(
    override val playerEntitiesByMaster: MutableMap<MasterEntityId, MutableSet<PlayerEntity>> = mutableMapOf()
  ) : Session() {
    fun activate(
      masterId: Long,
      masterEntityId: MasterEntityId,
      authorities: Set<Authority>
    ): ActiveConnection {
      return ActiveConnection(
        playerEntitiesByMaster = playerEntitiesByMaster,
        master = MasterEntity(
          masterId = masterId,
          entityId = masterEntityId
        ),
        currentActiveEntity = masterEntityId,
        authorities = authorities
      )
    }
  }

  data class PlayerEntity(
    val playerBestiaId: PlayerBestiaId,
    val entityId: EntityId
  )

  data class MasterEntity(
    val masterId: Long,
    val entityId: MasterEntityId
  )

  private val sessions = ConcurrentHashMap<AccountId, Session>()

  /**
   * Fully activating a session with a selected master.
   */
  fun activateSession(
    accountId: Long,
    masterId: Long,
    masterEntityId: MasterEntityId,
    authorities: Set<Authority>
  ) {
    LOG.info { "Activate session for account: $accountId with master entity id: $masterEntityId" }

    when (val session = getSession(accountId)) {
      is InactiveConnection -> {
        sessions[accountId] = session.activate(masterId, masterEntityId, authorities)
      }

      is ActiveConnection -> {
        if (session.master.entityId != masterEntityId) {
          deactivateSession(accountId)
          val newInactiveConnection = getSession(accountId) as InactiveConnection
          sessions[accountId] = newInactiveConnection.activate(masterId, masterEntityId, authorities)
        }
      }
    }
  }

  fun deactivateSession(accountId: Long) {
    LOG.info { "Deactivated session for account: $accountId" }

    val session = getSession(accountId)

    if (session is ActiveConnection) {
      sessions[accountId] = session.deactivate()
    }
  }

  fun registerPlayerBestiaEntity(
    accountId: AccountId,
    masterId: Long,
    playerBestiaId: PlayerBestiaId,
    playerBestiaEntityId: EntityId
  ) {
    val session = getSession(accountId)

    val store = session.playerEntitiesByMaster.getOrPut(masterId) { mutableSetOf() }
    store.add(PlayerEntity(playerBestiaId, playerBestiaEntityId))
  }

  fun getSelectedMasterEntityId(accountId: AccountId): MasterEntityId {
    val session = getSession(accountId)

    requireActiveSession(session, accountId)

    return session.master.entityId
  }

  fun getMasterId(accountId: AccountId): Long {
    val session = getSession(accountId)

    requireActiveSession(session, accountId)

    return session.master.masterId
  }

  fun getOwnedEntitiesByMaster(accountId: AccountId, masterId: Long): Set<PlayerEntity> {
    val session = getSession(accountId)

    return session.playerEntitiesByMaster[masterId] ?: emptySet()
  }

  fun activateEntity(
    accountId: AccountId,
    selectedEntityId: EntityId
  ) {
    LOG.info { "Activate entity: $selectedEntityId for account: $accountId" }

    val session = getSession(accountId)

    requireActiveSession(session, accountId)

    val activeMasterId = session.master.masterId
    val ownedEntities = session.playerEntitiesByMaster[activeMasterId] ?: emptySet()

    if (ownedEntities.none { it.entityId == selectedEntityId }) {
      throw EntityNotOwnedSessionException(accountId, selectedEntityId)
    }

    session.currentActiveEntity = selectedEntityId
  }

  fun getActiveEntityId(accountId: AccountId): EntityId {
    val session = getSession(accountId)

    requireActiveSession(session, accountId)

    return session.currentActiveEntity
  }

  fun hasAuthority(
    accountId: AccountId,
    authority: Authority
  ): Boolean {
    return when (val session = getSession(accountId)) {
      is ActiveConnection -> session.authorities.contains(authority)
      is InactiveConnection -> false
    }
  }

  private fun getSession(accountId: AccountId): Session {
    return sessions.getOrPut(accountId) {
      createInactiveSession(accountId)
    }
  }

  /**
   * Must be initially called when it is clear that a certain account exists.
   */
  private fun createInactiveSession(accountId: Long): InactiveConnection {
    require(!sessions.containsKey(accountId)) {
      // This should never happen as we only call this when not session exists.
      // It is a security check.
      "Session for account $accountId already exists"
    }

    return InactiveConnection()
  }

  @OptIn(ExperimentalContracts::class)
  private final fun requireActiveSession(session: Session, accountId: AccountId) {
    contract {
      returns() implies (session is ActiveConnection)
    }

    if (session !is ActiveConnection) {
      throw NoActiveSessionException(accountId)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}