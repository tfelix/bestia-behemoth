package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.Component

/**
 * Asks `net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem` to fill [Health] and [Mana] to their maxima once
 * it has rebuilt them, then removed by it. Added together with [IsStatusValueDirty] on a level-up
 * (`net.bestia.zone.battle.ecs.exp.GainExpSystem`): the new maxima only exist after the recalc, so filling the pools
 * any earlier would top them up to the old level's.
 */
data object RefillVitals : Component
