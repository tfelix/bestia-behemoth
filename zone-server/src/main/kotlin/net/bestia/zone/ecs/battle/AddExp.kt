package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType

class AddExp(
  var expToAdd: Int = 0
) : Component<AddExp> {

  override fun type() = AddExp

  companion object : ComponentType<AddExp>()
}