package net.bestia.zone.battle.ecs.status

import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.Component

/**
 * The [CombatValues] this entity fights with, read by combat and by every system that wants an effective attribute.
 * Every fighter has one; an entity without cannot fight.
 *
 * A holder around the shared value because `World.add` is deferred while systems iterate: assigning [values] is
 * immediate, so systems later in the same tick see what `StatusValueRecalcSystem` just resolved. Not `Dirtyable`: a
 * mob has no client, and the owner's window is fed by [OwnerCombatStats].
 */
class CombatStats(var values: CombatValues) : Component
