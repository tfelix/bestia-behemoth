package net.bestia.zone.trade

import net.bestia.bnet.proto.RequestTradeCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class RequestTradeCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: RequestTradeCmsgProto.RequestTradeCMSG): RequestTradeCMSG {
      return RequestTradeCMSG(
        playerId = accountId,
        targetEntityId = msg.targetEntityId
      )
    }
  }
}
