package net.bestia.zone.blackboard.ecs

import net.bestia.zone.ecs.core.Component

/** A quest board standing in the world. [structureId] is its player-built row, or null for a start town's board. */
class Blackboard(val structureId: Long?) : Component
