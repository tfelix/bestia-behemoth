package net.bestia.zone.ecs.visual

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType

data class Animation(
  var currentAnimation: String,
) : Component<Animation> {
  override fun type(): ComponentType<Animation> = Animation

  companion object : ComponentType<Animation>()
}