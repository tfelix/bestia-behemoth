package net.bestia.messages.entity

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.EntityMessage
import net.bestia.model.battle.Damage

/**
 * This message can be used to communicate a received damage to an entity. If
 * more then one damage is returned it will be a multi damage display. Otherwise
 * it is a simple hit.
 *
 * @author Thomas Felix
 */
data class EntityDamageMessage(
    override val accountId: Long,
    override val entityId: Long,
    @JsonProperty("d")
    val damage: List<Damage>
) : EntityMessage, AccountMessage {

  val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "entity.damage"
  }
}
