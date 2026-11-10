package net.bestia.zone.breeding.net

import net.bestia.bnet.proto.StartBreedingCmsgProto
import net.bestia.zone.message.CMSG

/** The master sends a pair to their farm with a Nesting Bundle. */
data class StartBreedingCMSG(
  override val playerId: Long,
  val structureId: Long,
  val motherEntityId: Long,
  val fatherEntityId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: StartBreedingCmsgProto.StartBreedingCMSG): StartBreedingCMSG {
      return StartBreedingCMSG(playerId = accountId, structureId = proto.structureId, motherEntityId = proto.motherEntityId, fatherEntityId = proto.fatherEntityId)
    }
  }
}
