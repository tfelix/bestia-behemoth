package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component

/**
 * The item an entity lying on the ground actually is: what, how many, and which
 * [net.bestia.zone.item.instance.ItemInstance] backs it.
 *
 * Server-only, and separate from [net.bestia.zone.ecs.entity.EntityVisual] on purpose - the client
 * needs the item id to pick a mesh and nothing else, while picking the stack up needs the amount and
 * the instance identity. Those were one component while the visual was item-specific.
 */
data class GroundItemStack(
  val itemId: Long,
  val amount: Int,

  /** Id of the backing `ItemInstance`; 0 means a plain item with no instance. */
  val uniqueId: Long = 0
) : Component
