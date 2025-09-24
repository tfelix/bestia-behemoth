package net.bestia.zone.ecs.persistence

import com.github.quillraven.fleks.EntityTag


/**
 * Entities with this tag are included in the persistence process.
 */
data object PersistAndRemove : EntityTag()