package net.bestia.zoneserver.entity.component

/**
 * Entities owning this component are able to equip items.
 *
 * @author Thomas Felix
 */
data class TemperatureComponent(
    override val entityId: Long,
    val minTolerableTemperature: Int,
    val maxTolerableTemperature: Int,
    val currentTemperature: Float
) : Component