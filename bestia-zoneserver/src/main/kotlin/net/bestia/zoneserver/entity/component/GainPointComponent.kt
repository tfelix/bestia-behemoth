package net.bestia.zoneserver.entity.component

data class GainPointComponent(
    override val entityId: Long,
    val gainPoints: Int
) : Component