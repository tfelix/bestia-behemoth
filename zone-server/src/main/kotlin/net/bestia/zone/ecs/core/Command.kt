package net.bestia.zone.ecs.core

/**
 * Marker for external intent enqueued into the simulation. Commands are the
 * *only* way for other threads (network, chat, scripting, ...) to influence ECS
 * state: they never mutate components directly.
 */
interface Command