package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs2.Component

/**
 * Entities with this tag are considered dead and their death logic will be executed.
 * This means:
 * - Loot is spawned
 * - EXP distributed
 */
data object Dead : Component