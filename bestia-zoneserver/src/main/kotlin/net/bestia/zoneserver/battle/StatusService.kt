package net.bestia.zoneserver.battle

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.StatusComponent

interface StatusService {
  fun calculateStatusPoints(entity: Entity): StatusComponent
  fun createsStatusFor(entity: Entity): Boolean
}