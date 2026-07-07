package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs.Component
import net.bestia.zone.ecs2.EntityId

data class Target(
  var entityId: EntityId,
) : Component
