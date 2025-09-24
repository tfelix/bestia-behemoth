package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType

class GivenExp(
  val value: Int
) : Component<GivenExp> {

  override fun type() = GivenExp

  companion object : ComponentType<GivenExp>()
}