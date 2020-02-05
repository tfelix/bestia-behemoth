package net.bestia.messages.inventory

import net.bestia.messages.AccountMessage

/**
 * The server confirms the casting of an item to the client.
 *
 * @author Thomas Felix
 */
data class InventoryItemCastConfirm(
    override val accountId: Long,
    val success: Boolean,
    val token: String
) : AccountMessage
