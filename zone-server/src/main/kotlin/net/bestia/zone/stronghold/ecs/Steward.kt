package net.bestia.zone.stronghold.ecs

import net.bestia.zone.ecs.core.Component

/** The steward of the Stronghold on seed [rank]. */
data class Steward(val rank: Int) : Component
