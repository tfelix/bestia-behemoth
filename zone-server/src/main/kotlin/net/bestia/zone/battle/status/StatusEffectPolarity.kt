package net.bestia.zone.battle.status

/** Whether an effect helps or harms its bearer; only harm goes past the damage gate. */
enum class StatusEffectPolarity {
  BUFF,
  DEBUFF,

  /** Bookkeeping such as a cooldown marker, which a healer may put on anybody. */
  NEUTRAL,
}
