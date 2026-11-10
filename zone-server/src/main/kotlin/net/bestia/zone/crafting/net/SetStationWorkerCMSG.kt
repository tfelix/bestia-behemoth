package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.SetStationWorkerCmsgProto
import net.bestia.zone.message.CMSG

/** The player picked who works a station's queue: one of their bestias, 0 for themselves, or any free crafter. */
data class SetStationWorkerCMSG(
  override val playerId: Long,
  val structureId: Long,
  val workerEntityId: Long,
  val anyFreeCrafter: Boolean = false
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, set: SetStationWorkerCmsgProto.SetStationWorkerCMSG): SetStationWorkerCMSG {
      return SetStationWorkerCMSG(
        playerId = accountId,
        structureId = set.structureId,
        workerEntityId = set.workerEntityId,
        anyFreeCrafter = set.anyFreeCrafter
      )
    }
  }
}
