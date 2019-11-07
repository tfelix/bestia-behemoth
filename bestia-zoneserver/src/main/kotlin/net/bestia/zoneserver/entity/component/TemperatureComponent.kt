package net.bestia.zoneserver.entity.component

import net.bestia.model.item.EquipmentSlot
import net.bestia.model.item.PlayerItemId

/**
 * Entities owning this component are able to equip items.
 *
 * @author Thomas Felix
 */
data class TemperatureComponent(
    override val entityId: Long,
    val minTolerableTemperature: Int,
    val maxTolerableTemperature: Int,
    val currentTemperature: Int
) : Component