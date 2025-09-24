package net.bestia.zone.ecs.player

import com.github.quillraven.fleks.EntityTag

/**
 * Marks the entity as an active player.
 * Required for:
 * - Updating the player AOI service
 */
data object ActivePlayer : EntityTag()