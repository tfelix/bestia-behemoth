package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs.Component
import net.bestia.zone.ecs.Entity

data class Target(
  var entity: Entity,
) : Component