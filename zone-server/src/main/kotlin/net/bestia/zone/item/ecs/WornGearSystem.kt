package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/**
 * Mirrors [Equipment] into [WornGear] every tick, the way [CarryCapacitySystem] mirrors the inventory. Equip,
 * unequip and revalidation all change [Equipment] alone; this one place makes each of them visible to others.
 */
@SpringComponent
class WornGearSystem : System {
  override val phase = Phase.ITEMS

  override val reads: ComponentClassSet = setOf(Equipment::class)
  override val writes: ComponentClassSet = setOf(WornGear::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(WornGear::class).each { id ->
      val equipment = world.get(id, Equipment::class) ?: return@each
      get<WornGear>().show(equipment.getWorn().mapValues { (_, item) -> item.itemId })
    }
  }
}
