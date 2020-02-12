package net.bestia.zoneserver.entity.factory

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator

abstract class EntityFactory(
    private val idGenerator: IdGenerator
) {

  protected fun newEntity(): Entity {
    val entityId = idGenerator.newId()

    return Entity(entityId)
  }
}