package net.bestia.entity.component.receiver

import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.Component

interface SyncReceiver {
  fun gatherReceiver(entity: Entity, component: Component, entityService: EntityService): Collection<Receiver>
}