package net.bestia.messages.entity

import net.bestia.messages.AccountMessage

import net.bestia.messages.EntityMessage

/**
 * This message is send to the clients if a visible component for the clients
 * was deleted and needs to be removed.
 *
 * @author Thomas Felix
 */
data class EntityComponentDelete(
    override val accountId: Long,
    override val entityId: Long,
    val componentId: Long
) : EntityMessage, AccountMessage
