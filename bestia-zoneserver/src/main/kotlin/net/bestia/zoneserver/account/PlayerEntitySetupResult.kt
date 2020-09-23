package net.bestia.zoneserver.account

/**
 * Contains initial information about the players entities
 * which are under his control.
 */
data class PlayerEntitySetupResult(
    val masterEntityId: Long,
    val playerBestiaIds: List<Long>
)