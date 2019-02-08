package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.actor.MessageApi
import net.bestia.zoneserver.entity.Entity

class ScriptRootContext : Context {
  val entityContexts = mutableListOf<EntityContext>()
  val newEntities = mutableListOf<Entity>()

  override fun commitEntityUpdates(messageApi: MessageApi) {
    entityContexts.forEach {
      it.commitEntityUpdates(messageApi)
    }
  }
}