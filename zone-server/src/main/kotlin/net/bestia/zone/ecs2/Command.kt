package net.bestia.zone.ecs2

/**
 * Marker for external intent enqueued into the simulation. Commands are the
 * *only* way for other threads (network, chat, scripting, ...) to influence ECS
 * state: they never mutate components directly.
 */
interface Command