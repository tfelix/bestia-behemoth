package net.bestia.zone.worldevent

import net.bestia.zone.casting.sense.BrewingEvents
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.HonorAct
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

  /** Whether the event ran its course before its time was up, without players ending it. Checked every run. */
  fun isOver(instance: WorldEventInstance): Boolean = false

  /** The ground masters hold by standing on it while the event runs, which counts for its contract; null for none. */
  fun heldArea(instance: WorldEventInstance): HeldArea? = null

  /** How far along the event is, as Observation tells it. */
  fun stageOf(instance: WorldEventInstance): BrewingEvents.Stage = BrewingEvents.Stage.BREAKING

  /** How many times further than a small event it is felt right now, or null for the catalogue's figure. */
  fun feltFrom(instance: WorldEventInstance): Double? = null

  /** The Bestia second it next moves on, such as a knot opening, or null when there is no such moment. */
  fun changesAtSecond(instance: WorldEventInstance): Long? = null

  /** The honor everyone who helped end it earns, here and in the nearest town. */
  fun honorAct(instance: WorldEventInstance): HonorAct = HonorAct.DEFEND_SETTLEMENT

  /** Takes back whatever the event put into the world, when it runs out or a GM cancels it. */
  fun end(instance: WorldEventInstance) {}

  /** Where it started and what it is about, if anything: an entity such as the apex, or a row such as a rift. */
  data class Started(val at: Vec3L, val subjectId: Long? = null)

  /** A circle in metres. */
  data class HeldArea(val x: Double, val y: Double, val radius: Double)
}
