package net.bestia.zone.honor

/** Everything that changes a Master's honor. What each one is worth lives in `honor.yml`. */
enum class HonorAct {
  KILL_NPC,
  KILL_PLAYER,

  /** An opposing-Order player at least 10 levels below the killer; an even fight between Orders is free. */
  KILL_WEAK_OPPOSING_PLAYER,
  KILL_PLAYER_BESTIA,
  STEAL_MUNDANE,
  STEAL_SUPERIOR,
  STEAL_RARE,
  STEAL_LEGENDARY,
  DAMAGE_BUILDING,
  DISPEL_WARD_STONE,
  DESTROY_WARD_STONE,
  ABANDON_COMMISSION,
  /** A master whose global honor is below zero. Never earns more than the victim's honor is below zero. */
  KILL_OUTLAW,

  /** A master who carries the THIEF mark. */
  KILL_THIEF,
  DEFEND_FROM_AGGRESSOR,
  HELP_NPC,
  COMPLETE_COMMISSION,
  RETURN_STOLEN_ITEM,
}
