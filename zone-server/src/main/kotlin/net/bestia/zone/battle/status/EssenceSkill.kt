package net.bestia.zone.battle.status

/**
 * An attack an essence lets its wearer use without having learned it: cast by itself when struck (Reflex), or at
 * will (Learned Trick). [skill] is its `skills.yml` identifier. An attack learned above [attackLevelCap] does nothing.
 */
data class EssenceSkill(val skill: String, val attackLevelCap: Int, val chancePercent: Int = 0)
