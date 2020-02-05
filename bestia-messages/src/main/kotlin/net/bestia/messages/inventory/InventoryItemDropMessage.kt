package net.bestia.messages.inventory

import net.bestia.messages.EntityMessage

/**
 * Send if the player wants to drop an item to the ground.
 *
 * @author Thomas Felix
 */
data class InventoryItemDropMessage(
    override val entityId: Long,
    val itemId: Int,
    val amount: Int
) : EntityMessage
