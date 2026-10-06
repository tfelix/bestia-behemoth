package net.bestia.zone.session

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
 * Read and changed from the tick, the IO lane and HTTP threads, so every change to a session is one atomic
 * `compute` and its collections are concurrent.
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
    @Volatile var currentActiveEntity: EntityId,
  ) : Session() {

    fun deactivate(): InactiveConnection {
      return InactiveConnection(
        playerEntitiesByMaster = playerEntitiesByMaster,
        authorities = authorities
      )
    }
  }

  private data class InactiveConnection(
    override val playerEntitiesByMaster: MutableMap<MasterEntityId, MutableSet<PlayerEntity>> = ConcurrentHashMap(),
    /**
     * Authorities granted to the account, established when the connection authenticates
     * (derived from the JWT role) and carried into the active session on master selection.
     */
    val authorities: Set<Authority> = emptySet()
  ) : Session() {
    fun activate(
      masterId: Long,
      masterEntityId: MasterEntityId
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

  /** Accounts holding a session; it should fall back to the connected count when they leave. */
  val sessionCount: Int
    get() = sessions.size

  /**
   * Registers the authorities granted to a freshly authenticated account. Must be called on
   * successful authentication, before a master is selected, so the authorities are available
   * when the session is later activated.
   */
  fun registerAuthenticatedConnection(
    accountId: Long,
    authorities: Set<Authority>
  ) {
    LOG.info { "Register authenticated connection for account: $accountId with authorities: $authorities" }

    sessions.compute(accountId) { _, session ->
      when (session) {
        null -> InactiveConnection(authorities = authorities)
        is InactiveConnection -> session.copy(authorities = authorities)
        is ActiveConnection -> session.copy(authorities = authorities)
      }
    }
  }

  /**
   * Fully activating a session with a selected master. The authorities established at
   * authentication time (see [registerAuthenticatedConnection]) are carried over.
   */
  fun activateSession(
    accountId: Long,
    masterId: Long,
    masterEntityId: MasterEntityId,
    /** Replaces what the session knew for [masterId]; read from the world's `OwnedBestia`, which outlives it. */
    ownedBestias: Collection<PlayerEntity> = emptyList(),
  ) {
    LOG.info { "Activate session for account: $accountId with master entity id: $masterEntityId" }

    sessions.compute(accountId) { _, session ->
      when (session) {
        null -> InactiveConnection().activate(masterId, masterEntityId)
        is InactiveConnection -> session.activate(masterId, masterEntityId)
        is ActiveConnection ->
          if (session.master.entityId == masterEntityId) session else session.deactivate().activate(masterId, masterEntityId)
      }
    }

    val owned = ConcurrentHashMap.newKeySet<PlayerEntity>().apply { addAll(ownedBestias) }
    sessions[accountId]?.playerEntitiesByMaster?.set(masterId, owned)
  }

  fun hasActiveSession(accountId: AccountId): Boolean {
    return sessions[accountId] is ActiveConnection
  }

  fun deactivateSession(accountId: Long) {
    LOG.info { "Deactivated session for account: $accountId" }

    sessions.computeIfPresent(accountId) { _, session ->
      if (session is ActiveConnection) session.deactivate() else session
    }
  }

  /** Forgets the account entirely, on disconnect. Its bestias stay in the world and keep their `OwnedBestia`. */
  fun removeSession(accountId: AccountId) {
    LOG.info { "Removed session for account: $accountId" }

    sessions.remove(accountId)
  }

  fun registerPlayerBestiaEntity(
    accountId: AccountId,
    masterId: Long,
    playerBestiaId: PlayerBestiaId,
    playerBestiaEntityId: EntityId
  ) {
    val session = sessions.computeIfAbsent(accountId) { InactiveConnection() }

    val store = session.playerEntitiesByMaster.computeIfAbsent(masterId) { ConcurrentHashMap.newKeySet() }
    store.add(PlayerEntity(playerBestiaId, playerBestiaEntityId))
  }

  fun getSelectedMasterEntityId(accountId: AccountId): MasterEntityId {
    val session = sessions[accountId]

    requireActiveSession(session, accountId)

    return session.master.entityId
  }

  fun getMasterId(accountId: AccountId): Long {
    val session = sessions[accountId]

    requireActiveSession(session, accountId)

    return session.master.masterId
  }

  fun getOwnedEntitiesByMaster(
    accountId: AccountId,
    masterId: Long
  ): Set<PlayerEntity> {
    val session = sessions[accountId] ?: return emptySet()

    return session.playerEntitiesByMaster[masterId]?.toSet() ?: emptySet()
  }

  fun activateEntity(
    accountId: AccountId,
    selectedEntityId: EntityId
  ) {
    LOG.info { "Activate entity: $selectedEntityId for account: $accountId" }

    val session = sessions[accountId]

    requireActiveSession(session, accountId)

    val activeMasterId = session.master.masterId
    val ownedEntities = session.playerEntitiesByMaster[activeMasterId] ?: emptySet()
    val isMaster = selectedEntityId == session.master.entityId

    if (!isMaster && ownedEntities.none { it.entityId == selectedEntityId }) {
      throw EntityNotOwnedSessionException(accountId, selectedEntityId)
    }

    session.currentActiveEntity = selectedEntityId
  }

  fun getActiveEntityId(accountId: AccountId): EntityId {
    val session = sessions[accountId]

    requireActiveSession(session, accountId)

    return session.currentActiveEntity
  }

  /**
   * The player bestia the account currently acts as, or null when that is the master itself.
   * Lets a handler write durable state to the right owner ([net.bestia.zone.bestia.PlayerBestia] vs
   * [net.bestia.zone.account.master.Master]) without the caller re-deriving it from the entity id.
   */
  fun getActivePlayerBestiaId(accountId: AccountId): PlayerBestiaId? {
    val session = sessions[accountId]

    requireActiveSession(session, accountId)

    val activeEntityId = session.currentActiveEntity
    if (activeEntityId == session.master.entityId) {
      return null
    }

    return session.playerEntitiesByMaster[session.master.masterId]
      ?.firstOrNull { it.entityId == activeEntityId }
      ?.playerBestiaId
  }

  fun getAuthorities(
    accountId: AccountId
  ): Set<Authority> {
    return when (val session = sessions[accountId]) {
      is ActiveConnection -> session.authorities
      is InactiveConnection, null -> emptySet()
    }
  }

  @OptIn(ExperimentalContracts::class)
  private final fun requireActiveSession(session: Session?, accountId: AccountId) {
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