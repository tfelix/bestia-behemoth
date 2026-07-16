package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/**
 * Describes the intent to add an item to the inventory component of an entity. Rather than every
 * caller (message handlers, chat commands, scripts, ...) duplicating the "check capacity, add to
 * inventory, persist" logic, they just attach one of these to the entity that should receive the
 * item; [ObtainItemIntentSystem] resolves it (and may reject it) on the next tick.
 *
 * This is grouped as a sealed class purely for documentation/exhaustiveness at call sites - the
 * ECS itself has no notion of it. [net.bestia.zone.ecs.core.World] stores and queries components
 * keyed by their concrete runtime type, never a declared base type, so [ObtainItemIntentSystem]
 * queries each subtype ([LootItemIntent], [CreateItemIntent]) separately rather than querying
 * `ObtainItemIntent::class` (which would never match anything).
 */
sealed class ObtainItemIntent : Component {
  /**
   * Attempt to pick up the item stack visualized by the ground entity [sourceEntityItemStackId].
   * Loots the whole stack atomically - there is no partial-amount loot.
   */
  class LootItemIntent(
    val sourceEntityItemStackId: EntityId,
  ) : ObtainItemIntent()

  /**
   * Creates [amount] of item [itemId] out of thin air (no source entity/stack involved) - e.g. the
   * `/item` GM command or a quest reward.
   */
  class CreateItemIntent(
    val itemId: Long,
    val amount: Int,
    val uniqueId: Long = 0,
  ) : ObtainItemIntent()
}
