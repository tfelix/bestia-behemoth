package net.bestia.messages.entity

import net.bestia.messages.AccountMessage

/**
 * This message is send if a component has changed and the clients data model
 * should be updated to reflect this change. The component data is added inside
 * the payload field.
 */
data class EntityComponentSync(
    override val accountId: Long,
    override val entityId: Long,
    val componentName: String,
    val payload: String,
    val latency: Int
) : EntityMessage, AccountMessage {

  init {
    require(latency >= 0) { "Latency can not be negative." }
  }
}
