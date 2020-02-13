package net.bestia.zoneserver.script.api

import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.entity.Entity

data class NewEntityCommand(
    val entity: Entity
) : EntityMessage {
  override val entityId: Long
    get() = entity.id
}