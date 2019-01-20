package net.bestia.zoneserver.entity.component

import net.bestia.model.item.EquipmentSlot
import net.bestia.model.item.Item

/**
 * Entities owning this component are able to equip items.
 *
 * @author Thomas Felix
 */
data class EquipComponent(
    override val entityId: Long,
    val allEquipmentSlots: MutableSet<EquipmentSlot> = mutableSetOf(),
    val equipments: MutableSet<Item> = mutableSetOf()
) : Component {

  /**
   * Returns the set of equipment slots which are available for equipping
   * items. Some items might have restrictions for equipping them into these
   * allEquipmentSlots whatsoever.
   *
   * @return A set of available equipment allEquipmentSlots.
   */
  val availableEquipmentSlots: Set<EquipmentSlot>
    get() = allEquipmentSlots - equipments.map { it.usedSlot }
}
