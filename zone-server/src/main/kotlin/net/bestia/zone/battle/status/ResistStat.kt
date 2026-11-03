package net.bestia.zone.battle.status

/** An attribute that helps an entity shake off a status effect, by its `status_effects.yml` short name. */
enum class ResistStat {
  STR,
  VIT,
  INT,
  DEX,
  WIL,
  AGI;

  fun valueIn(values: StatusValues): Int {
    return when (this) {
      STR -> values.strength
      VIT -> values.vitality
      INT -> values.intelligence
      DEX -> values.dexterity
      WIL -> values.willpower
      AGI -> values.agility
    }
  }
}
