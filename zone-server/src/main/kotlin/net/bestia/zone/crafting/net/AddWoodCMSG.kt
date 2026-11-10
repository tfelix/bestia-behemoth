package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.AddWoodCmsgProto
import net.bestia.zone.message.CMSG

/** The player put a log on one of their campfires. */
data class AddWoodCMSG(
  override val playerId: Long,
  val structureId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, addWood: AddWoodCmsgProto.AddWoodCMSG): AddWoodCMSG =
      AddWoodCMSG(playerId = accountId, structureId = addWood.structureId)
  }
}
