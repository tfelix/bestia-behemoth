package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.CancelQueueEntryCmsgProto
import net.bestia.zone.message.CMSG

/** The player took an entry off a station's queue. */
data class CancelQueueEntryCMSG(
  override val playerId: Long,
  val structureId: Long,
  val index: Int
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, cancel: CancelQueueEntryCmsgProto.CancelQueueEntryCMSG): CancelQueueEntryCMSG {
      return CancelQueueEntryCMSG(playerId = accountId, structureId = cancel.structureId, index = cancel.index)
    }
  }
}
