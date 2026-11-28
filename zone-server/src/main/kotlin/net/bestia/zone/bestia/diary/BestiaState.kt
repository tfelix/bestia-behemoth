package net.bestia.zone.bestia.diary

/** The few states an owned bestia's time is counted in, for its master's away report. */
enum class BestiaState {
  WORKING,
  IDLE,
  RESTING,

  /** Its work stopped and waits for its master: the chest is full, the fire is out. */
  PAUSED,
  FIGHTING,
  SHELTERING,
  TRAVELLING,
}
