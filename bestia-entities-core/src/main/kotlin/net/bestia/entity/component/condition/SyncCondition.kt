package net.bestia.entity.component.condition

import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.Component

/**
 * Checks if the component meets certain precondition and should be synced to the listed receiver.
 */
interface SyncCondition {
  fun doSync(receiver: Entity, entity: Entity, component: Component, entityService: EntityService): Boolean
}