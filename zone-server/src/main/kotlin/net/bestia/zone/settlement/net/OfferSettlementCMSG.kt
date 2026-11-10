package net.bestia.zone.settlement.net

import net.bestia.bnet.proto.OfferSettlementCmsgProto
import net.bestia.zone.message.CMSG

data class OfferSettlementCMSG(
  override val playerId: Long,
  val signEntityId: Long,
  val masterName: String,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: OfferSettlementCmsgProto.OfferSettlementCMSG): OfferSettlementCMSG {
      return OfferSettlementCMSG(playerId = accountId, signEntityId = msg.signEntityId, masterName = msg.masterName)
    }
  }
}
