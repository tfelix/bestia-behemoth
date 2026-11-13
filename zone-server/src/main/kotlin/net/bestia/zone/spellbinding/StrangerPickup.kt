package net.bestia.zone.spellbinding

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.GroundPickupRule
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Fires a "stranger picks it up" binding on whoever takes its item off the ground, unless that is the binder's side. */
@Component
class StrangerPickup(
  private val book: SpellBindingBook,
  private val firing: SpellBindingFiring,
) : GroundPickupRule {

  override val reads: ComponentClassSet = setOf(GroundItemStack::class) + BindingTargets.READS
  override val writes: ComponentClassSet = emptySet()

  override fun mayPickUp(world: World, looter: EntityId, stack: EntityId): Boolean {
    return true
  }

  override fun onPickUp(world: World, looter: EntityId, stack: EntityId) {
    val uniqueId = world.get(stack, GroundItemStack::class)?.uniqueId?.takeIf { it != 0L } ?: return
    val entry = book.byItem(uniqueId)?.takeIf { it.bound.trigger == BindingTrigger.STRANGER_PICKS_UP } ?: return
    val targets = BindingTargets(world, entry.bound.owner)
    if (!targets.isStranger(looter) || !targets.admits(entry.bound.filter, looter)) {
      return
    }
    val at = world.get(stack, Position::class)?.toVec3L() ?: return

    world.post { firing.fire(this, entry, at, looter) }
  }
}
