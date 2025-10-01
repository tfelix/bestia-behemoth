package net.bestia.zone.ecs2

import net.bestia.zone.util.EntityId

interface OnEntityRemovedListener {
  fun onEntityRemoved(entityId: EntityId)
}