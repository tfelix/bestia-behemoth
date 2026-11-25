package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.Component

/**
 * A creature a world event put into the world: which event, and what that event is about. Killing one counts
 * towards the event's contract. Here rather than with the events, so the death code may read it.
 */
data class EventCreature(val event: String, val subjectId: Long) : Component
