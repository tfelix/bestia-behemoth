package net.bestia.zone.ecs.network

import com.github.quillraven.fleks.EntityTag

/**
 * Tag which needs to be set if an entity has changed in a way which requires a re-sync via the network.
 * Later it maybe could make sense to rather mark every component if its dirty on its own.
 */
data object IsDirty : EntityTag()