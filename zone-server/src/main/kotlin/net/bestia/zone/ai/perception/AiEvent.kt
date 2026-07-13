package net.bestia.zone.ai.perception

import net.bestia.zone.util.EntityId

/**
 * A discrete stimulus produced by perception (or combat) that the think stage may react to. Events
 * are queued on the `Brain` and drained each think tick. The set an NPC actually reacts to is
 * declared per archetype via `perception.reacts_to`.
 */
data class AiEvent(
  val type: AiEventType,
  val sourceEntityId: EntityId?,
  val timestampMs: Long
)

