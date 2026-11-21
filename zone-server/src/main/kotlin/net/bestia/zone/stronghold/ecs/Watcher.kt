package net.bestia.zone.stronghold.ecs

import net.bestia.zone.ecs.core.Component

/** A watcher of the Stronghold on seed [rank]. */
data class Watcher(val rank: Int) : Component
