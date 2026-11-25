package net.bestia.zone.worldevent

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.worldevent.persistence.WorldEventInstance

/**
 * What runs one kind of event. The director decides when and where; this decides what happens. Off the tick:
 * world actions go through `WorldView`.
 */
interface WorldEvent {

  /** The name `world_events.yml` refers to it by. */
  val script: String get() = javaClass.simpleName.removeSuffix("Event")

  /** Whether the province offers what this event needs, beyond the mana the catalogue asks for. */
  fun fits(signals: ProvinceSignals): Boolean = true

  /** Starts the event in the province, or null when it cannot start there after all. */
  fun start(signals: ProvinceSignals): Started?

  /** Whether the event was ended by players, for example its subject was killed. Checked every run. */
  fun isDefeated(instance: WorldEventInstance): Boolean = false

  /** Takes back whatever the event put into the world, when it runs out or a GM cancels it. */
  fun end(instance: WorldEventInstance) {}

  /** Where it started and the entity it is about, if any. */
  data class Started(val at: Vec3L, val subjectEntityId: Long? = null)
}
