package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component

/**
 * Nothing may reduce this entity's health.
 *
 * Every path that takes health away checks for it, which is two: `ReceivedDamageSystem` and
 * `EnvironmentalExposureSystem`. A third one added later has to check as well, and the way to notice is
 * that `InvulnerabilityTest` names them.
 *
 * A marker of its own rather than a `Townsfolk` check inside the battle system. What the battle system
 * needs to know is that this thing cannot be hurt; who it is and why is not its business, and would make
 * the settlement layer a dependency of combat.
 *
 * Townsfolk carry it because they are non-combatant this release. A settlement's roster comes from the
 * world generator and nothing puts a person back, so a killable baker is a bakery that closes forever the
 * first time somebody idly swings at it.
 */
data object Invulnerable : Component
