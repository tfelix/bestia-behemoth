package net.bestia.zone.ecs.player

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType

data class Master(
  var masterId: Long,
) : Component<Master> {

  override fun type(): ComponentType<Master> = Master

  companion object : ComponentType<Master>()
}