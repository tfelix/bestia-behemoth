package net.bestia.zone.ecs.core

/** The three steps of [EcsWorld.tick], in order; each is timed on its own. */
enum class TickStage {
  /** Posted work at the start of the tick: tick-lane messages, leases, skill resolutions. */
  POSTED,

  /** The scheduler: deciding which systems are due and running them. */
  SYSTEMS,

  /** Structural changes the systems asked for. */
  DEFERRED,
}
