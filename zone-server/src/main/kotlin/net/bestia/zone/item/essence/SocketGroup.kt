package net.bestia.zone.item.essence

import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.EquipmentSlot

/** Which items an essence fits. Every species has one group, like a Ragnarok Online card. */
enum class SocketGroup {
  WEAPON,
  SHIELD,
  ARMOR,
  GARMENT,
  FOOTGEAR,
  HEADGEAR,
  ACCESSORY,
  TOOL;

  companion object {
    /** Null for an item no essence fits, such as a material. */
    fun of(template: ItemTemplateRegistry.Template): SocketGroup? {
      if (template.toolKind != null) return TOOL

      return when (template.equipSlot) {
        EquipmentSlot.RIGHT_HAND -> WEAPON
        EquipmentSlot.LEFT_HAND -> SHIELD
        EquipmentSlot.ARMOR -> ARMOR
        EquipmentSlot.GARMENT -> GARMENT
        EquipmentSlot.FOOTGEAR -> FOOTGEAR
        EquipmentSlot.HEAD_UPPER, EquipmentSlot.HEAD_MID -> HEADGEAR
        EquipmentSlot.ACCESSORY_1, EquipmentSlot.ACCESSORY_2, EquipmentSlot.LIGHT_SOURCE -> ACCESSORY
        null -> null
      }
    }
  }
}
