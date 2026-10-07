package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component

/**
 * How much of a ground stack's integrity is gone, out of its material's
 * [net.bestia.zone.item.material.ItemMaterialSpec.integrity].
 *
 * Counted up from zero, so a retuned material also applies to stacks already lying on the ground. Not sent to
 * clients: an item shows no health bar.
 */
class GroundItemIntegrity(
  var lost: Int = 0
) : Component
