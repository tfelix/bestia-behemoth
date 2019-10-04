package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.entity.Entity

data class NewEntityCommand(
    val entity: Entity
) : EntityCommand {
  override fun toEntityEnvelope(): EntityEnvelope {
    return EntityEnvelope(entityId = entity.id, content = this)
  }
}