package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.stat.ItemRoll

/**
 * The item an entity lying on the ground actually is: what, how many, and which
 * [net.bestia.zone.item.persistence.ItemInstance] backs it.
 *
 * Server-only, and separate from [net.bestia.zone.entity.ecs.EntityVisual] on purpose - the client
 * needs the item id to pick a mesh and nothing else, while picking the stack up needs the amount and
 * the instance identity. Those were one component while the visual was item-specific.
 */
data class GroundItemStack(
  val itemId: Long,
  val amount: Int,

  /** Id of the backing `ItemInstance`; 0 means a plain item with no instance. */
  val uniqueId: Long = 0,

  /** Who crafted it and where, kept so a pile picked back up lands on its own maker's pile. */
  val provenance: ItemProvenance? = null,

  /**
   * What a forged copy rolled, for one that has no instance row yet: it overflowed its crafter's bag. Picking it
   * up makes the row with this roll.
   */
  val roll: ItemRoll? = null,
) : Component
