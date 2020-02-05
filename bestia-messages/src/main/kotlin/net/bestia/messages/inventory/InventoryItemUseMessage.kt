package net.bestia.messages.inventory

import net.bestia.messages.EntityMessage

/**
 * Signals the server to use an castable item on the map possibly spawning map
 * entities or doing things to the map/zone itself.
 *
 * @author Thomas Felix
 */
data class InventoryItemUseMessage(
    override val entityId: Long,

    val playerItemId: Int,
    /**
     * Token for identifying the cast request on the client and receive the
     * confirm message.
     */
    val token: String,
    val x: Long = 0,
    val y: Long = 0,
    val z: Long = 0
) : EntityMessage
