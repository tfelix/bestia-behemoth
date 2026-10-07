package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.Component

/**
 * A creature: a master, a mob or a player's bestia. `Health` does not say this, because promoted props and
 * construction sites have health too, and they must not bleed.
 */
data object Living : Component
