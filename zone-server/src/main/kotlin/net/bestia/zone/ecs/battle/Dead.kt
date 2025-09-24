package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.EntityTag

/**
 * Entities with this tag are considered dead and their death logic will be executed.
 */
data object Dead : EntityTag()