package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentId
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Removable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId
import java.time.Instant

/**
 * The enchantments of an item lying on the ground, copied from its rows when it fell. Next to [GroundItemStack], so
 * Dispel can aim at it and everyone nearby sees it glow.
 */
data class ItemEnchantments(
  val enchantments: List<ActiveItemEnchantment>,
  /** When this copy reached the ground: the start of the client's countdown ring. */
  val since: Instant,
) : Component, Removable {
  override val dirtyFlag = DirtyFlag()

  fun find(enchantment: ItemEnchantmentId): ActiveItemEnchantment? {
    return enchantments.find { it.enchantment == enchantment }
  }

  fun without(enchantment: ItemEnchantmentId): ItemEnchantments {
    return copy(enchantments = enchantments.filterNot { it.enchantment == enchantment })
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return ItemEnchantmentsComponentSMSG.of(entityId, this, Instant.now(), removed)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }
}
