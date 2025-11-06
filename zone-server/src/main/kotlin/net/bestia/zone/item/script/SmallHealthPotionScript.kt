package net.bestia.zone.item.script

import net.bestia.zone.ecs.Entity
import org.springframework.stereotype.Component

@Component
class SmallHealthPotionScript : ItemScript {
  override val itemId = 3L

  override fun execute(user: Entity): Boolean {
    TODO("Not yet implemented")
  }
}