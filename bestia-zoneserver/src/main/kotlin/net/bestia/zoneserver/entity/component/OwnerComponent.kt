package net.bestia.zoneserver.entity.component

/**
 * Marks the entity owned by a client account. This could be
 * for example enable this account to control the entity.
 */
data class OwnerComponent(
    override val entityId: Long,
    val ownerAccountIds: Set<Long>
) : Component