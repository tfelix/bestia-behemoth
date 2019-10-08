package net.bestia.zoneserver.entity.component

data class ItemComponent(
    override val entityId: Long,
    val itemId: Long,
    val itemDbName: String
) : Component
