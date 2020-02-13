package net.bestia.messages.entity

import net.bestia.messages.AccountMessage

/**
 * By sending this message the client wants to get to know how he is able to
 * interact with the given entity. The server will respond with a list of
 * possible interactions.
 *
 * @author Thomas Felix
 */
class EntityInteractionRequest(
    override val accountId: Long,
    override val entityId: Long,
    val interactedEntityId: Long
) : EntityMessage, AccountMessage
