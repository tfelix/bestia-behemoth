package net.bestia.zone.ecs.core.scenario

import net.bestia.zone.ecs.core.Component

// --- Components (passive data) -------------------------------------------------

class Position(var x: Float = 0f, var y: Float = 0f) : Component
class Velocity(var dx: Float = 0f, var dy: Float = 0f) : Component
class Health(var value: Int = 100, val max: Int = 100) : Component

/** Marks a critter that idly wanders (mirrors passive-wanderer's idle_wander). */
class Wander(var step: Int = 0) : Component
