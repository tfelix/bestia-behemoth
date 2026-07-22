package net.bestia.zone.item.equip

import net.bestia.bnet.proto.UnequipItemCMSGProto
import net.bestia.zone.message.CMSG

data class UnequipItemCMSG(
  override val playerId: Long,
  val slot: EquipmentSlot
) : CMSG {

  companion object {
    /** Returns null for a slot ordinal the server does not know - see [EquipItemCMSG.fromBnet]. */
    fun fromBnet(playerId: Long, bnet: UnequipItemCMSGProto.UnequipItemCMSG): UnequipItemCMSG? {
      val slot = EquipmentSlot.entries.getOrNull(bnet.slot) ?: return null

      return UnequipItemCMSG(playerId, slot)
    }
  }
}
