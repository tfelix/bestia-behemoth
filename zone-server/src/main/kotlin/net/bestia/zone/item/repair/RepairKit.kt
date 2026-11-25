package net.bestia.zone.item.repair

import net.bestia.zone.item.ecs.ItemTemplateRegistry

/** The two repair kits and the gear each one mends. */
enum class RepairKit(val itemId: Long) {
  /** `weapon_repair_kit` in items.yml. */
  WEAPON(641L) {
    override fun mends(template: ItemTemplateRegistry.Template): Boolean {
      return template.weaponType != null
    }
  },

  /** `armor_repair_kit` in items.yml. */
  ARMOR(642L) {
    override fun mends(template: ItemTemplateRegistry.Template): Boolean {
      return template.armorType != null
    }
  };

  abstract fun mends(template: ItemTemplateRegistry.Template): Boolean
}
