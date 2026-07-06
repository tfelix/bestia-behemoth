package net.bestia.zone.ai.planner

/**
 * The symbolic boolean facts the GOAP planner reasons over. Kept deliberately small; extend by
 * adding a key here and teaching [WorldStateBuilder] how to derive it and the relevant actions how
 * to require/produce it.
 */
enum class StateKey {
  HAS_TARGET,
  TARGET_IN_MELEE_RANGE,
  TARGET_DEAD,
  SELF_SAFE,
  AT_WANDER_POINT
}
