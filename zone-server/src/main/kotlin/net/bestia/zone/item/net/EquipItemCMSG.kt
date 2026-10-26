package net.bestia.zone.item.net

import net.bestia.bnet.proto.EquipItemCMSGProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.item.equip.EquipmentSlot

data class EquipItemCMSG(
  override val playerId: Long,
  val playerItemId: Long,
  val slot: EquipmentSlot
) : CMSG {

  companion object {
    /**
     * Returns null for a slot ordinal the server does not know - a client sending garbage should be
     * ignored, not crash the dispatch.
     */
    fun fromBnet(playerId: Long, bnet: EquipItemCMSGProto.EquipItemCMSG): EquipItemCMSG? {
      val slot = EquipmentSlot.entries.getOrNull(bnet.slot) ?: return null

      return EquipItemCMSG(playerId, bnet.playerItemId, slot)
    }
  }
}
