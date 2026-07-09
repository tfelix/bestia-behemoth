package net.bestia.zone.ecs.core.scenario

import net.bestia.zone.ecs.core.Command
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

// --- Components (passive data) -------------------------------------------------

class Position(var x: Float = 0f, var y: Float = 0f) : Component
class Velocity(var dx: Float = 0f, var dy: Float = 0f) : Component
class Health(var value: Int = 100, val max: Int = 100) : Component

/** Marks a critter that idly wanders (mirrors passive-wanderer's idle_wander). */
class Wander(var step: Int = 0) : Component

// --- Inbound command (external intent) ----------------------------------------

/** Sent from a "network" thread to steer an entity. */
class MoveCommand(val entity: EntityId, val dx: Float, val dy: Float) : Command

// --- Outbound event -----------------------------------------------------------

/** Emitted whenever an entity actually moved; drained by the messaging layer. */
data class EntityMoved(val entity: EntityId, val x: Float, val y: Float)
