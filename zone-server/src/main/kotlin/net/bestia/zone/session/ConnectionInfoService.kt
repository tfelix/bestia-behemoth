package net.bestia.zone.session

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.MasterEntityId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.context.ApplicationEventPublisher
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
 * Every time a master starts or stops being played it publishes a [MasterPresenceChangedEvent], after the
 * change, on the thread that made it.
 *
 * TODO this should later probably also be in the Redis service?
 */
@Service
class ConnectionInfoService(
  private val events: ApplicationEventPublisher = ApplicationEventPublisher { },
) {

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

    var replaced: MasterEntity? = null
    var changed = false
    sessions.compute(accountId) { _, session ->
      when (session) {
        null -> InactiveConnection().activate(masterId, masterEntityId).also { changed = true }
        is InactiveConnection -> session.activate(masterId, masterEntityId).also { changed = true }
        is ActiveConnection ->
          if (session.master.entityId == masterEntityId) {
            session
          } else {
            replaced = session.master
            changed = true
            session.deactivate().activate(masterId, masterEntityId)
          }
      }
    }

    val owned = ConcurrentHashMap.newKeySet<PlayerEntity>().apply { addAll(ownedBestias) }
    sessions[accountId]?.playerEntitiesByMaster?.set(masterId, owned)

    replaced?.let { publishPresence(accountId, it.masterId, online = false) }
    if (changed) {
      publishPresence(accountId, masterId, online = true)
    }
  }

  /**
   * True while [accountId] plays [masterId]. The guard for every hop from an account to "its" entity: an
   * account that switched to another master must not get that master the party's EXP or vitals. One map read,
   * so fine on the tick.
   */
  fun isPlaying(accountId: AccountId, masterId: Long): Boolean {
    return activeMasterIdOf(accountId) == masterId
  }

  fun hasActiveSession(accountId: AccountId): Boolean {
    return sessions[accountId] is ActiveConnection
  }

  /** The selected master of [accountId], or null without an active session. One map read, so fine on the tick. */
  fun activeMasterIdOf(accountId: AccountId): Long? {
    return (sessions[accountId] as? ActiveConnection)?.master?.masterId
  }

  /** Every account playing a master right now, with that master's id. A snapshot; it does not follow later changes. */
  fun activeMasters(): Map<AccountId, Long> {
    val active = HashMap<AccountId, Long>()
    for ((accountId, session) in sessions) {
      if (session is ActiveConnection) {
        active[accountId] = session.master.masterId
      }
    }
    return active
  }

  fun deactivateSession(accountId: Long) {
    LOG.info { "Deactivated session for account: $accountId" }

    var left: MasterEntity? = null
    sessions.computeIfPresent(accountId) { _, session ->
      if (session is ActiveConnection) {
        left = session.master
        session.deactivate()
      } else {
        session
      }
    }

    left?.let { publishPresence(accountId, it.masterId, online = false) }
  }

  /** Forgets the account entirely, on disconnect. Its bestias stay in the world and keep their `OwnedBestia`. */
  fun removeSession(accountId: AccountId) {
    LOG.info { "Removed session for account: $accountId" }

    val removed = sessions.remove(accountId)
    if (removed is ActiveConnection) {
      publishPresence(accountId, removed.master.masterId, online = false)
    }
  }

  private fun publishPresence(accountId: AccountId, masterId: Long, online: Boolean) {
    events.publishEvent(MasterPresenceChangedEvent(this, accountId, masterId, online))
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
   * Lets a handler write durable state to the right owner ([net.bestia.zone.account.persistence.PlayerBestia] vs
   * [net.bestia.zone.account.persistence.Master]) without the caller re-deriving it from the entity id.
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

  /** What the account was granted on authenticating, master selected or not; [getAuthorities] needs a master. */
  fun getGrantedAuthorities(
    accountId: AccountId
  ): Set<Authority> {
    return when (val session = sessions[accountId]) {
      is ActiveConnection -> session.authorities
      is InactiveConnection -> session.authorities
      null -> emptySet()
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