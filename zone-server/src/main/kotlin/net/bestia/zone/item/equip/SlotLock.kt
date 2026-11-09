package net.bestia.zone.item.equip

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId

/**
 * A slot a strip skill emptied, which stays empty while its status lasts. Accessories and the light source
 * have none: they cannot be stripped. One headgear status locks both head slots.
 */
enum class SlotLock(val slot: EquipmentSlot, val status: StatusEffectId) {
  HEAD_UPPER(EquipmentSlot.HEAD_UPPER, StatusEffectId.HEADGEAR_STRIPPED),
  HEAD_MID(EquipmentSlot.HEAD_MID, StatusEffectId.HEADGEAR_STRIPPED),
  ARMOR(EquipmentSlot.ARMOR, StatusEffectId.ARMOR_STRIPPED),
  GARMENT(EquipmentSlot.GARMENT, StatusEffectId.GARMENT_STRIPPED),
  FOOTGEAR(EquipmentSlot.FOOTGEAR, StatusEffectId.FOOTGEAR_STRIPPED),
  WEAPON(EquipmentSlot.RIGHT_HAND, StatusEffectId.DISARMED),
  SHIELD(EquipmentSlot.LEFT_HAND, StatusEffectId.SHIELD_STRIPPED);

  companion object {
    fun lockedSlots(effects: StatusEffects?): Set<EquipmentSlot> {
      if (effects == null) {
        return emptySet()
      }

      return entries.filter { effects.hasEffect(it.status.id) }.map { it.slot }.toSet()
    }
  }
}
