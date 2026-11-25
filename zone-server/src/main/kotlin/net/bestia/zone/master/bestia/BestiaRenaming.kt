package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.ecs.BestiaTag
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.DisplayName
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Names a master's party bestia. Names are not unique: every master calls their bestias what they like, and
 * the owner on the name tag tells two Rexes apart.
 */
@Service
class BestiaRenaming(
  private val connections: ConnectionInfoService,
  private val worldView: WorldView,
  private val playerBestias: PlayerBestiaRepository,
  private val ownedBestias: OwnedBestiasPublisher,
  private val out: OutMessageProcessor,
  private val asyncJobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
) {

  /** A blank [raw] name gives the bestia back its species' name. */
  fun rename(accountId: AccountId, entityId: EntityId, raw: String) {
    val name = if (raw.isBlank()) null else DisplayName.normalizeWordsOrNull(raw, MAX_NAME_LENGTH)
    if (raw.isNotBlank() && name == null) {
      out.sendToPlayer(accountId, OperationErrorSMSG(OpError.BESTIA_NAME_INVALID))
      return
    }

    val masterId = connections.getMasterId(accountId)
    // An honest client only offers its own party bestias.
    val owned = worldView.read { get(entityId, OwnedBestia::class) }?.takeIf { it.masterId == masterId } ?: return

    // Keyed by the master like every save of its bestias, so the write-behind cannot interleave.
    asyncJobs.submit(key = masterId) {
      val ownerName = transactions.execute {
        val row = playerBestias.findByIdForUpdate(owned.playerBestiaId)?.takeIf { it.master.id == masterId }
          ?: return@execute null
        row.name = name

        row.master.name
      } ?: return@submit

      worldView.modify(entityId) { id ->
        if (get(id, OwnedBestia::class) == owned) {
          add(id, BestiaTag(name, ownerName))
        }
      }
      ownedBestias.publish(accountId)
    }
  }

  companion object {
    const val MAX_NAME_LENGTH = 24
  }
}
