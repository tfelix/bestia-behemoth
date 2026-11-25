package net.bestia.zone.item.keepsake

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.GroundPickupRule
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** While its ward holds, a fallen bestia's bag is its master's alone. */
@Component
class KeepsakePickup : GroundPickupRule {

  override val reads: ComponentClassSet = setOf(ItemEnchantments::class, Master::class)
  override val writes: ComponentClassSet = emptySet()

  override fun mayPickUp(world: World, looter: EntityId, stack: EntityId): Boolean {
    val owner = KeepsakeWardEnchantment.masterOf(world, stack) ?: return true

    return world.get(looter, Master::class)?.masterId == owner
  }

  override fun onPickUp(world: World, looter: EntityId, stack: EntityId) {
  }
}
