package net.bestia.zone.ecs

import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

@Service
class ActivePlayerAOIService : AreaOfInterestService<Long>(), OnEntityRemovedListener {
  override fun onEntityRemoved(entityId: EntityId) {
    removeEntityPosition(entityId)
  }
}

