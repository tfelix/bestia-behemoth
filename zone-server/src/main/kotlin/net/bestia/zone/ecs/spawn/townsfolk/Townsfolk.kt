package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.zone.ecs.core.Component

/**
 * Marks an entity as one settlement's inhabitant, and says which one.
 *
 * Absence is meaningful, as it is for `Ambient`: a creature without this was not put here by the
 * settlement layer and must not be torn down by it.
 *
 * Not `Dirtyable`. Which household somebody belongs to is server bookkeeping; what the client draws is the
 * same visual any other bestia has.
 */
data class Townsfolk(val identity: Long) : Component
