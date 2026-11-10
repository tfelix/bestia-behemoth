package net.bestia.zone.breeding.net

import net.bestia.bnet.proto.ReleaseBreedingPairCmsgProto
import net.bestia.zone.message.CMSG

/** The master lets a farm's pair go. */
data class ReleaseBreedingPairCMSG(
  override val playerId: Long,
  val structureId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: ReleaseBreedingPairCmsgProto.ReleaseBreedingPairCMSG): ReleaseBreedingPairCMSG {
      return ReleaseBreedingPairCMSG(playerId = accountId, structureId = proto.structureId)
    }
  }
}
