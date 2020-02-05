package net.bestia.messages.entity

import net.bestia.messages.AccountMessage
import net.bestia.messages.EntityMessage

enum class Interaction {
  TALK,
  ATTACK,
  LOOT,
  GENERIC
}

/**
 * By sending this message to the client the client is informed how
 * he will be able to interact with this entity.
 *
 * @author Thomas Felix
 */
data class EntityInteractionMessage(
    override val accountId: Long,
    override val entityId: Long,
    val interactions: Set<Interaction>
) : AccountMessage, EntityMessage
