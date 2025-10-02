package net.bestia.zone.ecs.player

import net.bestia.zone.ecs.Component

/**
 * Marks the entity as an active player.
 * Required for:
 * - Updating the player AOI service
 */
data object ActivePlayer : Component