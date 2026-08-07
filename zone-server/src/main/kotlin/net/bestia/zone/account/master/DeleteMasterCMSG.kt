package net.bestia.zone.account.master

import net.bestia.bnet.proto.DeleteMasterProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.AccountId

/**
 * Request to permanently delete one of the account's masters.
 *
 * Unlike most handlers this one cannot resolve its subject from the session
 * ([net.bestia.zone.ecs.core.session.ConnectionInfoService.getActiveEntityId]): deletion happens on the
 * character selection screen, before any master has been picked, so the id has to come from the client.
 * [MasterDeletionService] therefore checks ownership itself instead of relying on the session.
 */
data class DeleteMasterCMSG(
  override val playerId: AccountId,
  val masterId: Long,
  /** What the player typed into the confirmation prompt; must match the master's name. */
  val confirmationName: String
) : CMSG {
  companion object {
    fun fromBnet(accountId: AccountId, msg: DeleteMasterProto.DeleteMasterCMSG): DeleteMasterCMSG {
      return DeleteMasterCMSG(
        playerId = accountId,
        masterId = msg.masterId,
        confirmationName = msg.confirmationName
      )
    }
  }
}
