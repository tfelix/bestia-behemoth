package net.bestia.zone.ai.goap2.bestia

/**
 * Keys attack-effectiveness memory by [targetArchetype] (e.g. "golem"), not by the individual
 * target's entity id — a fight is one-off, but "fire hurts golems" is knowledge that transfers to
 * every future encounter with the same kind of enemy.
 */
data class EffectivenessKey(val targetArchetype: String, val attackId: String)
