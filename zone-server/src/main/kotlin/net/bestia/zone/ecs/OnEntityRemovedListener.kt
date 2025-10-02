package net.bestia.zone.ecs

import net.bestia.zone.util.EntityId

interface OnEntityRemovedListener {
  fun onEntityRemoved(entityId: EntityId)
}