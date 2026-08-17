package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component

/**
 * Marks an entity whose [Health] / [Mana] / [Stamina] **maxima** are derived from its level and
 * primary attributes by [net.bestia.zone.battle.status.ConditionValueCalculator], and are therefore
 * re-derived by `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem` whenever those inputs
 * change. Player-owned entities carry it: `net.bestia.zone.account.master.MasterEntitySpawner` and
 * `net.bestia.zone.bestia.PlayerBestiaEntitySpawner`.
 *
 * Mobs deliberately do **not**: `net.bestia.zone.bestia.BestiaEntitySpawner` gives them the authored
 * `Bestia.health` from their species row, which is content and must survive a recalc. They still get
 * their [StatusValues] and speed rebuilt like anything else, so buffs and slows work on them - it is
 * only the pool maxima that are off limits. Without this gate a mob's pool gets recomputed with the
 * *player* formula, and since a mob has no `Level` that means level 1: a 500 HP boss becomes 18 HP
 * the first time anything applies a status effect to it, permanently, because
 * [net.bestia.zone.battle.status.CurMax]'s `max` setter clamps `current` down with it.
 *
 * Server-side bookkeeping only - deliberately not [net.bestia.zone.ecs.core.Dirtyable], since the
 * client learns the resulting pools from [Health] / [Mana] / [Stamina] themselves and has no use for
 * knowing how they were arrived at.
 */
data object FormulaDrivenVitals : Component
