package net.bestia.zone.cartography.net

import net.bestia.bnet.proto.SurveyStopsCmsgProto
import net.bestia.zone.message.CMSG

data class SurveyStopsCMSG(
  override val playerId: Long,
  val structureId: Long,
  val stopsMs: List<Long>,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: SurveyStopsCmsgProto.SurveyStopsCMSG): SurveyStopsCMSG {
      return SurveyStopsCMSG(accountId, proto.structureId, proto.stopsMsList.toList())
    }
  }
}
