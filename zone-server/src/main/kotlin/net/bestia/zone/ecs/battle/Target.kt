package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.EntityId

data class Target(
  var entityId: EntityId,
) : Component
