package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.OpenCraftingCmsgProto
import net.bestia.zone.message.CMSG

/** The player opened the crafting window, from a hotkey rather than by casting a skill or clicking a station. */
data class OpenCraftingCMSG(
  override val playerId: Long,

  /** The skill to show first, or `CraftingService.NO_SKILL`. */
  val skillId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, openCrafting: OpenCraftingCmsgProto.OpenCraftingCMSG): OpenCraftingCMSG =
      OpenCraftingCMSG(playerId = accountId, skillId = openCrafting.skillId)
  }
}
