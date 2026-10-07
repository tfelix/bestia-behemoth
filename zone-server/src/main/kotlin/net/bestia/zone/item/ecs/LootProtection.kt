package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Kill loot that only the killer's account may pick up, for a few seconds. Not persisted, so a restart frees
 * it early.
 */
class LootProtection(
  val ownerAccountId: Long,
  var remainingSeconds: Float,
) : Component
