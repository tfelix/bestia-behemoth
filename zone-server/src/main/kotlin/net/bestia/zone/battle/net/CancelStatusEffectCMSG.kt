package net.bestia.zone.battle.net

import net.bestia.bnet.proto.CancelStatusEffectCmsgProto
import net.bestia.zone.message.CMSG

data class CancelStatusEffectCMSG(
  override val playerId: Long,
  val effectId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: CancelStatusEffectCmsgProto.CancelStatusEffectCMSG): CancelStatusEffectCMSG {
      return CancelStatusEffectCMSG(accountId, proto.effectId)
    }
  }
}
