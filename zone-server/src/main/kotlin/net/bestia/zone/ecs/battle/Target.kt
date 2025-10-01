package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs2.Component
import net.bestia.zone.ecs2.Entity

data class Target(
  var entity: Entity,
) : Component