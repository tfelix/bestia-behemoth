package net.bestia.zone.skill

/**
 * What kind of bestia skill a catalogue row is, as the design docs sort the Attack List
 * (`bestia_blueprint.yaml` `archetypes`). A generic script reads it, so one script serves every attack of
 * an archetype.
 */
enum class SkillArchetype {
  STRIKE,
  POWER_STRIKE,
  SHOT,
  BOLT,
  DOT,
  AOE,
  ULTIMATE,
  HEAL,
  BUFF,
  DEBUFF,

  /** Not a fight skill: gathering, scouting, a status for the master. */
  UTILITY;

  /** Whether the wild AI may use it in a fight. */
  val isCombat: Boolean
    get() {
      return this != UTILITY
    }
}
