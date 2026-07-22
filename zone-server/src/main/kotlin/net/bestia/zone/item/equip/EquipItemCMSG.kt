package net.bestia.zone.item.equip

import net.bestia.bnet.proto.EquipItemCMSGProto
import net.bestia.zone.message.CMSG

data class EquipItemCMSG(
  override val playerId: Long,
  val itemId: Long,
  val uniqueId: Long,
  val slot: EquipmentSlot
) : CMSG {

  companion object {
    /**
     * Returns null for a slot ordinal the server does not know - a client sending garbage should be
     * ignored, not crash the dispatch.
     */
    fun fromBnet(playerId: Long, bnet: EquipItemCMSGProto.EquipItemCMSG): EquipItemCMSG? {
      val slot = EquipmentSlot.entries.getOrNull(bnet.slot) ?: return null

      return EquipItemCMSG(playerId, bnet.itemId, bnet.uniqueId, slot)
    }
  }
}
