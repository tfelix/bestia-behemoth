package net.bestia.entity.component.condition

import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.Component

class TrueCondition : SyncCondition {
  override fun doSync(receiver: Entity, entity: Entity, component: Component, entityService: EntityService): Boolean {
    return true
  }

}