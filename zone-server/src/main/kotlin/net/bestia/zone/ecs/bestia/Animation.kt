package net.bestia.zone.ecs.bestia

import net.bestia.zone.ecs.core.Component

data class Animation(
  var currentAnimation: String,
) : Component