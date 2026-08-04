package net.bestia.zone.navigation

import net.bestia.zone.ecs.core.Component

/**
 * Which movement profile this entity plans routes with.
 *
 * The identifier rather than the resolved profile, so that reloading movement configuration does not mean
 * walking the world's entities to re-point them - and so the component stays a small value that costs nothing
 * to copy or persist.
 *
 * Absent means the default; see `MovementProfileRegistry.getOrDefault`. Nothing has to add this component for
 * navigation to work, which is what lets an existing world of unconfigured bestia keep moving.
 */
data class MovementCapability(val profileId: String) : Component
