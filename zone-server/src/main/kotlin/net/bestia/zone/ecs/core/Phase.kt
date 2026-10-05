package net.bestia.zone.ecs.core

/**
 * The coarse order of a tick. Every system of a phase runs after every system of the phases before it; inside a
 * phase, [System.after] orders the systems that touch the same components. See [TickOrder].
 */
enum class Phase {
  /** Perceive, think and act: what the AI decides this tick. */
  AI,

  /** Paths are walked and entities change position. */
  MOVEMENT,

  /** Casts, crafts, constructions and respawns: actions that run over time. */
  ACTIONS,

  /** Terrain, props and the ground: what is streamed and drawn around players. */
  WORLD,

  /** Status effects run down and status values are rebuilt from them. */
  STATUS,

  /** Attacks and area effects hit, and the damage they staged is applied. */
  COMBAT,

  /** Health, mana and stamina regenerate. */
  RECOVERY,

  /** Items, experience and carry capacity. */
  ITEMS,

  /** The dead are handled and removed. */
  DEATH,

  /** Creatures and townsfolk appear and disappear around players. */
  SPAWN,

  /** Slow bookkeeping: logouts, exposure, trade range, the economy, decay. */
  UPKEEP,

  /** Entities leaving the world are written back. */
  PERSIST,
}
