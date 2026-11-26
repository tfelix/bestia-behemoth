package net.bestia.zone.worldevent.raid

import net.bestia.zone.casting.sense.BrewingEvents
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.HonorAct
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.ProvinceSignals
import net.bestia.zone.worldevent.WorldEvent
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.springframework.stereotype.Component

/**
 * Brigands eye a rich town. They rob travellers on the road and walk up to their camp in the hills, so their tracks
 * lead there. Players who follow them can clear the camp for a day. Then the band marches on the town, plunders it
 * for a few hours and leaves. Killing the chief and the band ends the raid, at the camp or in the town.
 */
@Component
class TownRaidEvent(
  private val towns: RaidTowns,
  private val band: RaidBand,
  private val rumours: RumourService,
  private val clock: BestiaClock,
  private val world: WorldView,
) : WorldEvent {

  /** Raids whose march the towns already heard of. Lost on restart, which tells the news once more. */
  private val marched = HashSet<Long>()

  override fun fits(signals: ProvinceSignals): Boolean = world.read { towns.targetIn(this, signals.province.index) } != null

  override fun start(signals: ProvinceSignals): WorldEvent.Started? {
    val town = world.read { towns.targetIn(this, signals.province.index) } ?: return null
    val camp = towns.campFor(town) ?: return null

    val chief = world.read { band.muster(this, script, signals.province.index, from = camp.ambush, home = camp.at) }
    rumours.post(RumourKind.ROAD_ROBBED, camp.ambush.x, camp.ambush.y, RUMOUR_STRENGTH)

    return WorldEvent.Started(camp.at, chief)
  }

  override fun isDefeated(instance: WorldEventInstance): Boolean {
    val chief = instance.subjectId ?: return true
    if (!world.read { band.isBroken(this, chief) }) return false

    end(instance)
    rumours.post(RumourKind.RAIDERS_ROUTED, instance.x, instance.y, RUMOUR_STRENGTH)
    return true
  }

  /** Leads the band to its camp, then to the town; over once it has plundered the town and left. */
  override fun isOver(instance: WorldEventInstance): Boolean {
    val chief = instance.subjectId ?: return false
    val now = clock.now().absoluteSecond
    if (now >= leavesAtSecond(instance)) {
      leave(instance)
      return true
    }

    val marching = now >= marchesAtSecond(instance)
    val home = if (marching) townOf(instance) else Vec3L(instance.x, instance.y, instance.z)
    home?.let { world.read { band.lead(this, script, instance.provinceIndex, chief, it) } }
    if (marching && marched.add(instance.id)) {
      home?.let { rumours.post(RumourKind.RAIDERS_MARCH, it.x, it.y, MARCH_STRENGTH) }
    }
    return false
  }

  override fun stageOf(instance: WorldEventInstance): BrewingEvents.Stage =
    if (isMarching(instance)) BrewingEvents.Stage.BREAKING else BrewingEvents.Stage.GATHERING

  override fun changesAtSecond(instance: WorldEventInstance): Long =
    if (isMarching(instance)) leavesAtSecond(instance) else marchesAtSecond(instance)

  /** The camp while the band sits in it, the town once it marches. */
  override fun heldArea(instance: WorldEventInstance): WorldEvent.HeldArea? {
    if (!isMarching(instance)) return WorldEvent.HeldArea(instance.x.toDouble(), instance.y.toDouble(), CAMP_HELD_METRES)
    val town = townOf(instance) ?: return null
    return WorldEvent.HeldArea(town.x.toDouble(), town.y.toDouble(), TOWN_HELD_METRES)
  }

  /** Outlaws caught in their camp, or a town defended against them. */
  override fun honorAct(instance: WorldEventInstance): HonorAct =
    if (isMarching(instance)) HonorAct.DEFEND_SETTLEMENT else HonorAct.KILL_OUTLAW

  override fun end(instance: WorldEventInstance) {
    marched.remove(instance.id)
    instance.subjectId?.let { chief -> world.read { band.disband(this, chief) } }
  }

  private fun leave(instance: WorldEventInstance) {
    end(instance)
    townOf(instance)?.let { rumours.post(RumourKind.TOWN_RAIDED, it.x, it.y, MARCH_STRENGTH) }
  }

  private fun townOf(instance: WorldEventInstance): Vec3L? =
    towns.townNearest(instance.x.toDouble(), instance.y.toDouble())

  private fun isMarching(instance: WorldEventInstance): Boolean = clock.now().absoluteSecond >= marchesAtSecond(instance)

  private fun marchesAtSecond(instance: WorldEventInstance): Long = instance.startedAtSecond + CAMP_SECONDS

  private fun leavesAtSecond(instance: WorldEventInstance): Long = marchesAtSecond(instance) + PLUNDER_SECONDS

  private companion object {
    /** A Bestia day in the hills, an evening of real time to follow the tracks. */
    const val CAMP_SECONDS = 24 * 3600L

    /** Three Bestia hours in the streets, about one real hour, before they leave with what they took. */
    const val PLUNDER_SECONDS = 3 * 3600L

    const val CAMP_HELD_METRES = 150.0
    const val TOWN_HELD_METRES = 300.0

    const val RUMOUR_STRENGTH = 0.6

    /** A band in the streets is news everywhere around. */
    const val MARCH_STRENGTH = 0.9
  }
}
