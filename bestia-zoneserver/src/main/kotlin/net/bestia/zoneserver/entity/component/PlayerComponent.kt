package net.bestia.zoneserver.entity.component

data class PlayerComponent(
    override val entityId: Long,
    val ownerAccountId: Long,
    val playerBestiaId: Long
) : Component