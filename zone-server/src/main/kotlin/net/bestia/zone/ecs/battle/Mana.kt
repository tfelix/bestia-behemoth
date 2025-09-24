package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import net.bestia.zone.status.CurMax

class Mana(
  current: Int,
  max: Int
) : Component<Mana> {

  private val data = CurMax().apply {
    this.max = max
    this.current = current
  }

  var current
    get() = data.current
    set(value) {
      data.current = value
    }

  var max
    get() = data.max
    set(value) {
      data.max = value
    }

  override fun type() = Mana

  companion object : ComponentType<Mana>()
}