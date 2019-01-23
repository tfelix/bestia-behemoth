package net.bestia.zoneserver.entity.component

import net.bestia.model.item.EquipmentSlot
import net.bestia.model.item.PlayerItemId

/**
 * Entities owning this component are able to equip items.
 *
 * @author Thomas Felix
 */
data class EquipComponent(
    override val entityId: Long,
    val slots: Map<EquipmentSlot, PlayerItemId?> = EquipmentSlot.values()
        .map { it to null }
        .toMap()
) : Component {

  /**
   * Returns the set of equipment slots which are available for equipping
   * items. Some items might have restrictions for equipping them into these
   * allEquipmentSlots whatsoever.
   *
   * @return A set of available equipment allEquipmentSlots.
   */
  val availableEquipmentSlots: Set<EquipmentSlot>
    get() = slots.filter { a -> a.value == null }.map { it.key }.toSet()
}
