package net.bestia.zoneserver.entity.component

import net.bestia.zoneserver.actor.entity.transmit.ClientTransmitFilter
import net.bestia.zoneserver.actor.entity.transmit.OwnerTransmitFilter

/**
 * Entities owning this component are able to equip items.
 *
 * @author Thomas Felix
 */
@ClientTransmitFilter(OwnerTransmitFilter::class)
data class TemperatureComponent(
    override val entityId: Long,
    val minTolerableTemperature: Int,
    val maxTolerableTemperature: Int,
    val currentTemperature: Float
) : Component