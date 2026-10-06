package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.Component

/**
 * Marks an entity as needing its [StatusValues] recomputed from [BaseStatusValues] + active status
 * effects. Added whenever something that feeds the recalc changes (a status effect applied, or one
 * expiring - see `net.bestia.zone.battle.StatusEffectService` and
 * `net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem`), and removed by
 * `net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem` once it has processed the entity -
 * same query-process-strip pattern as `net.bestia.zone.battle.ecs.exp.GainExp`.
 */
data object IsStatusValueDirty : Component
