package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.persistence.StatusEffectPersistenceService
import net.bestia.zone.item.instance.ItemInstanceRepository
import net.bestia.zone.party.PartyService
import net.bestia.zone.util.AccountId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Permanently deletes a master and everything that only existed because of it.
 *
 * The counterpart to [MasterFactory], and like it the database half only - it does not despawn anything,
 * because it refuses to run at all while the master is still in the world (see [Denial.IN_USE]). Deletion
 * is offered on the character selection screen, where by definition no master is selected.
 *
 * ### What goes with the master
 * Owned bestias, both item containers' worth of inventory (the master's and each bestia's), learned skills
 * and the master's persisted status effects. Three of those ride along on JPA cascades declared in
 * [Master]; the rest are cleaned up here because nothing links them to the master by a cascading
 * association:
 *
 * - **Party membership** ([PartyService.detachDeletedMaster]) - `Party.owner` is a non-null FK, so a master
 *   that owns a party cannot simply be deleted underneath it.
 * - **Crafted items** ([ItemInstanceRepository.clearCraftedByMaster]) - an item this master forged may be in
 *   somebody else's hands and must outlive its maker, so the reference is nulled rather than followed.
 * - **Held item instances** - the container cascade deletes the *slots* but deliberately not the
 *   [net.bestia.zone.item.instance.ItemInstance] rows in them (an instance outlives its placement so it
 *   survives being moved). With their owner gone nothing can ever reach them again, so they are deleted
 *   here, after the slots that pointed at them.
 * - **Persisted status effects** - stored against [Master.entityId], which is not a foreign key to anything.
 */
@Service
class MasterDeletionService(
  private val masterRepository: MasterRepository,
  private val partyService: PartyService,
  private val itemInstanceRepository: ItemInstanceRepository,
  private val statusEffectPersistenceService: StatusEffectPersistenceService,
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
) {

  enum class Denial {
    /** The master does not exist, or exists but belongs to a different account - reported as one reason. */
    NOT_OWNED,
    NAME_MISMATCH,
    IN_USE
  }

  sealed interface Result {
    /**
     * [partyResult] is what the master's party membership turned into, or null when it had none. The caller
     * owns telling the remaining members - this service does not send messages.
     */
    data class Deleted(
      val masterName: String,
      val partyResult: PartyService.LeavePartyResult?
    ) : Result

    data class Denied(val reason: Denial) : Result
  }

  @Transactional
  fun delete(accountId: AccountId, masterId: Long, confirmationName: String): Result {
    val master = masterRepository.findByIdOrNull(masterId)

    // A master of somebody else's and a master that never existed answer identically, so deleting cannot be
    // used to find out which ids are real.
    if (master == null || master.account.id != accountId) {
      LOG.warn { "Account $accountId tried to delete master $masterId which it does not own" }

      return Result.Denied(Denial.NOT_OWNED)
    }

    // Re-checked here rather than trusted from the client: the typed name is the only thing standing between
    // a misrouted click and a character that cannot be brought back.
    if (master.name != confirmationName.trim()) {
      return Result.Denied(Denial.NAME_MISMATCH)
    }

    if (isStillInTheWorld(accountId, master)) {
      LOG.info { "Refusing to delete master $masterId for account $accountId: it is still live in the world" }

      return Result.Denied(Denial.IN_USE)
    }

    // Everything the log line and the reply need is read out now: once the delete below has been flushed,
    // touching the master's lazy associations again is reaching into a row that is gone.
    val masterName = master.name
    val masterEntityId = master.entityId
    val ownedBestias = master.bestias.ownedBestias

    // Read out before the cascade takes the slots away, deleted after it has.
    val heldInstanceIds = (listOf(master.container) + ownedBestias.map { it.container })
      .flatMap { container -> container.slots }
      .mapNotNull { slot -> slot.itemInstance?.id }

    val partyResult = partyService.detachDeletedMaster(master)

    itemInstanceRepository.clearCraftedByMaster(masterId)

    // Removed from the account's collection as well as deleted: `Account.master` cascades ALL but has no
    // orphanRemoval, so a still-listed master would be cascade-persisted again on flush - after we deleted it.
    master.account.master.remove(master)
    masterRepository.delete(master)

    // Forces the master and its cascaded container slots out before the instances those slots referenced are
    // deleted, so the delete does not run into the still-present foreign key.
    masterRepository.flush()

    if (heldInstanceIds.isNotEmpty()) {
      itemInstanceRepository.deleteAllById(heldInstanceIds)
    }

    statusEffectPersistenceService.deleteFor(listOf(masterEntityId))

    LOG.info {
      "Deleted master $masterId ('$masterName') of account $accountId, " +
          "including ${ownedBestias.size} bestias and ${heldInstanceIds.size} item instances"
    }

    return Result.Deleted(masterName, partyResult)
  }

  /**
   * Normally false - deletion is offered on the selection screen, before a master is picked. It is true
   * while a master is playing, and briefly after a logout until `PersistAndRemoveSystem` has written it
   * back; deleting in that window would race the persist job into re-inserting rows behind us.
   *
   * The owned bestias are checked as well as the master itself. They are the reason the session is
   * consulted at all: a [net.bestia.zone.bestia.PlayerBestia] has no stored entity id to look up, its id is
   * minted at spawn, so the session's record of what it spawned for this master is the only way to find
   * them. Each is re-checked against the world because that record is not cleared on disconnect and can
   * outlive the entities it names.
   */
  private fun isStillInTheWorld(accountId: AccountId, master: Master): Boolean {
    if (world.hasEntity(master.entityId)) {
      return true
    }

    return connectionInfoService.getOwnedEntitiesByMaster(accountId, master.id)
      .any { world.hasEntity(it.entityId) }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
