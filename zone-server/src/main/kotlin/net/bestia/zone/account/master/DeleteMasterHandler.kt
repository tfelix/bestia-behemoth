package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.party.DisbandPartySMSG
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

/**
 * Deletes one of the account's masters at its request. Everything that decides whether that is allowed lives
 * in [MasterDeletionService]; this only turns its answer into a wire message.
 *
 * Deliberately not `@Transactional`: the deletion commits inside the service, so the party members notified
 * afterwards are told about something that has actually happened, and a failure to reach them cannot undo it.
 */
@Component
class DeleteMasterHandler(
  private val masterDeletionService: MasterDeletionService,
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<DeleteMasterCMSG> {
  override val handles = DeleteMasterCMSG::class

  override fun handle(msg: DeleteMasterCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val result = try {
      masterDeletionService.delete(msg.playerId, msg.masterId, msg.confirmationName)
    } catch (e: Exception) {
      LOG.error(e) { "Failed to delete master ${msg.masterId} for account ${msg.playerId}" }

      outMessageProcessor.sendToPlayer(
        msg.playerId,
        OperationErrorSMSG(OperationErrorProto.OpError.MASTER_GENERAL_ERROR)
      )

      return true
    }

    when (result) {
      is MasterDeletionService.Result.Denied -> {
        outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(result.reason.toOpError()))
      }

      is MasterDeletionService.Result.Deleted -> {
        // The client re-fetches the list via GetMaster when the selection screen reloads, so only the
        // acknowledgement is pushed - same as master creation.
        outMessageProcessor.sendToPlayer(msg.playerId, MasterDeletedSMSG)

        notifyFormerParty(result.partyResult)
      }
    }

    return true
  }

  /**
   * Brings whoever shared a party with the deleted master back in sync. The deleting player is not among
   * them: they are sitting on the character selection screen with no party UI to correct.
   */
  private fun notifyFormerParty(partyResult: PartyService.LeavePartyResult?) {
    try {
      when (partyResult) {
        null -> Unit

        is PartyService.LeavePartyResult.Disbanded -> {
          val disbandMsg = DisbandPartySMSG(partyResult.partyId)
          partyResult.notifiedAccountIds.forEach { outMessageProcessor.sendToPlayer(it, disbandMsg) }
        }

        is PartyService.LeavePartyResult.Left -> {
          partyResult.remainingMemberAccountIds.forEach { accountId ->
            val partyInfo = partyService.getPartyInfoForAccount(accountId) ?: return@forEach
            outMessageProcessor.sendToPlayer(accountId, partyInfo)
          }
        }
      }
    } catch (e: Exception) {
      // The master is already gone; a party roster that failed to refresh is worth a log line, not a
      // failure reported back to a player whose deletion succeeded.
      LOG.warn(e) { "Could not notify the former party of a deleted master" }
    }
  }

  private fun MasterDeletionService.Denial.toOpError(): OperationErrorProto.OpError = when (this) {
    MasterDeletionService.Denial.NOT_OWNED -> OperationErrorProto.OpError.MASTER_NOT_OWNED
    MasterDeletionService.Denial.NAME_MISMATCH -> OperationErrorProto.OpError.MASTER_NAME_MISMATCH
    MasterDeletionService.Denial.IN_USE -> OperationErrorProto.OpError.MASTER_IN_USE
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
