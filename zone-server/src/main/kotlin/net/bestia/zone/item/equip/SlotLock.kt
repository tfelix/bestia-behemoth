package net.bestia.zone.item.equip

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId

/** A slot a strip skill emptied, which stays empty while its status lasts. */
enum class SlotLock(val slot: EquipmentSlot, val status: StatusEffectId) {
  ARMOR(EquipmentSlot.ARMOR, StatusEffectId.ARMOR_STRIPPED),
  WEAPON(EquipmentSlot.RIGHT_HAND, StatusEffectId.DISARMED);

  companion object {
    fun lockedSlots(effects: StatusEffects?): Set<EquipmentSlot> {
      if (effects == null) {
        return emptySet()
      }

      return entries.filter { effects.hasEffect(it.status.id) }.map { it.slot }.toSet()
    }
  }
}
