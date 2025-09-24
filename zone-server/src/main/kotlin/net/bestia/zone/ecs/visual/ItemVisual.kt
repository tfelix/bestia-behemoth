package net.bestia.zone.ecs.visual

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType

data class ItemVisual(
  val id: Int,
) : Component<ItemVisual> {

  override fun type(): ComponentType<ItemVisual> = ItemVisual

  companion object : ComponentType<ItemVisual>()
}