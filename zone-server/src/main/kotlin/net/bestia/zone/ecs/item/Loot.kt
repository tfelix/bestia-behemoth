package net.bestia.zone.ecs.item

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType

data class Loot(
  val itemId: Long,
  val amount: Int,
  val uniqueId: Long = 0 // 0 means nothing special.
) : Component<Loot> {
  override fun type(): ComponentType<Loot> = Loot

  companion object : ComponentType<Loot>()
}