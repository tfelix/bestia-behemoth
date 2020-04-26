package net.bestia.zoneserver.status

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.StatusComponent

interface StatusComponentFactory {
  fun buildComponent(entity: Entity): StatusComponent
  fun canBuildStatusFor(entity: Entity): Boolean
}