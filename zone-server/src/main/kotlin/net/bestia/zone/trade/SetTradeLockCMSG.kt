package net.bestia.zone.trade

import net.bestia.bnet.proto.SetTradeLockCmsgProto
import net.bestia.zone.message.CMSG

data class SetTradeLockCMSG(
  override val playerId: Long,
  val tradeId: Long,
  val locked: Boolean,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: SetTradeLockCmsgProto.SetTradeLockCMSG): SetTradeLockCMSG {
      return SetTradeLockCMSG(
        playerId = accountId,
        tradeId = msg.tradeId,
        locked = msg.locked
      )
    }
  }
}
