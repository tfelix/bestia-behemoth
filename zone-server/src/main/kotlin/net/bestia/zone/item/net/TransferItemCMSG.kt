package net.bestia.zone.item.net

import net.bestia.bnet.proto.TransferItemCMSGProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class TransferItemCMSG(
  override val playerId: Long,
  val fromEntityId: EntityId,
  val toEntityId: EntityId,
  val playerItemId: Long,
  val amount: Int,
) : CMSG {
  companion object {
    fun fromBnet(playerId: Long, bnet: TransferItemCMSGProto.TransferItemCMSG): TransferItemCMSG {
      return TransferItemCMSG(playerId, bnet.fromEntityId, bnet.toEntityId, bnet.playerItemId, bnet.amount)
    }
  }
}
