package net.bestia.zoneserver.status

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.OriginalStatusComponent

interface OriginalStatusComponentFactory {
  fun buildComponent(entity: Entity): OriginalStatusComponent
  fun canBuildStatusFor(entity: Entity): Boolean
}