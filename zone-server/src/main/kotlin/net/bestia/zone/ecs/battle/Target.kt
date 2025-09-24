package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity

data class Target(
  val entity: Entity,
) : Component<Target> {

  override fun type() = Target

  companion object : ComponentType<Target>()
}