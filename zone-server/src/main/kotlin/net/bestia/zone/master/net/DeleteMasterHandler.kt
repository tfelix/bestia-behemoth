package net.bestia.zone.master.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.guild.GuildInfoPublisher
import net.bestia.zone.party.PartyRosterPublisher
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component
import net.bestia.zone.master.MasterDeletedSMSG
import net.bestia.zone.master.MasterDeletionService

/**
 * Deletes one of the account's masters at its request. Everything that decides whether that is allowed lives
 * in [MasterDeletionService]; this only turns its answer into a wire message.
 *
 * Deliberately not `@Transactional`: the deletion commits inside the service, so the party and guild members
 * notified afterwards are told about something that has actually happened, and a failure to reach them cannot
 * undo it.
 */
@Component
class DeleteMasterHandler(
  private val masterDeletionService: MasterDeletionService,
  private val partyRosterPublisher: PartyRosterPublisher,
  private val guildInfoPublisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor
) : IoMessageHandler<DeleteMasterCMSG> {
  override val wire = decoder(MessageCase.DELETE_MASTER) { accountId, envelope ->
    DeleteMasterCMSG.fromBnet(accountId, envelope.deleteMaster)
  }

  override fun handle(msg: DeleteMasterCMSG): Boolean {
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
        outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(result.reason.toOpError(), result.args))
      }

      is MasterDeletionService.Result.Deleted -> {
        // The client re-fetches the list via GetMaster when the selection screen reloads, so only the
        // acknowledgement is pushed - same as master creation.
        outMessageProcessor.sendToPlayer(msg.playerId, MasterDeletedSMSG)

        notifyFormerGroups(result)
      }
    }

    return true
  }

  /**
   * Brings whoever shared a party or a guild with the deleted master back in sync. The deleting player is not
   * among them: the master is not being played, so the publishers leave it out.
   */
  private fun notifyFormerGroups(result: MasterDeletionService.Result.Deleted) {
    when (val partyResult = result.partyResult) {
      null -> Unit
      is PartyService.LeavePartyResult.Disbanded -> partyRosterPublisher.disbanded(partyResult.partyId, partyResult.members)
      is PartyService.LeavePartyResult.Left -> partyRosterPublisher.removed(partyResult.partyId, partyResult.member)
    }

    result.guildId?.let { guildInfoPublisher.request(it) }
  }

  private fun MasterDeletionService.Denial.toOpError(): OperationErrorProto.OpError = when (this) {
    MasterDeletionService.Denial.NOT_OWNED -> OperationErrorProto.OpError.MASTER_NOT_OWNED
    MasterDeletionService.Denial.NAME_MISMATCH -> OperationErrorProto.OpError.MASTER_NAME_MISMATCH
    MasterDeletionService.Denial.IN_USE -> OperationErrorProto.OpError.MASTER_IN_USE
    MasterDeletionService.Denial.LEADS_GUILD -> OperationErrorProto.OpError.GUILD_LEADER_MUST_DISBAND
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
