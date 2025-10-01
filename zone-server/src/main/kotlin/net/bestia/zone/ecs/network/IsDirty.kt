package net.bestia.zone.ecs.network

import net.bestia.zone.ecs2.Component

/**
 * Tag which needs to be set if an entity has changed in a way which requires a re-sync via the network.
 */
data object IsDirty : Component