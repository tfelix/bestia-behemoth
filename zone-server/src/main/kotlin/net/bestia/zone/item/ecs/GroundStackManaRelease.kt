package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.mana.ManaEmission
import org.springframework.stereotype.Component

/**
 * A stack of mana crystals that is destroyed or crumbles away on the ground lets its mana go where it lay. So
 * the field only loses mana for good when crystals are used up in a craft.
 */
@Component
class GroundStackManaRelease(
  private val itemTemplates: ItemTemplateRegistry,
  private val emission: ManaEmission,
) {

  val reads: ComponentClassSet = setOf(GroundItemStack::class, Position::class)

  fun release(world: World, stackId: EntityId) {
    val stack = world.get(stackId, GroundItemStack::class) ?: return
    val at = world.get(stackId, Position::class)?.toVec3L() ?: return
    val identifier = itemTemplates.identifierOf(stack.itemId) ?: return

    emission.onStoreBroken(at, identifier, stack.amount)
  }
}
