package net.bestia.zone.ai.goap2.bestia

/**
 * A single attack a bestia can throw at a target, in whatever detail the planner needs to reason
 * about it: how close it has to be ([range]) and how expensive it nominally is ([baseCost]) before
 * remembered [EffectivenessKey] effectiveness adjusts that cost up or down.
 *
 * Stands in for the real skill system (`net.bestia.zone.battle.skill.BattleSkill` /
 * `KnownSkills`) until goap2 is wired into the ECS — see [AttackEffectiveness] for why that wiring is
 * deliberately not part of this domain yet.
 */
data class AttackDefinition(val id: String, val range: Long, val baseCost: Float = 5f)
